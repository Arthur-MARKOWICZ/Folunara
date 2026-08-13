package com.arthur.ereader.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.arthur.ereader.core.files.FormatTools
import com.github.junrar.Archive
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.CRC32
import java.util.zip.CheckedOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class ConvertedComic(val uri: Uri, val size: Long)

class CbrConversionException(message: String, cause: Throwable? = null) : IOException(message, cause)

@Singleton
class CbrConverter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
) {
    fun destinationUri(source: Uri, displayName: String): Uri = Uri.fromFile(destination(source, displayName))

    suspend fun convert(source: Uri, displayName: String): ConvertedComic = withContext(Dispatchers.IO) {
        val directory = conversionDirectory().apply {
            if (!exists() && !mkdirs()) throw CbrConversionException("Não foi possível preparar o armazenamento para a conversão.")
        }
        val destination = destination(source, displayName)
        val sourceCopy = File.createTempFile("cbr-source-", ".rar", context.cacheDir)
        val output = File.createTempFile("cbr-output-", ".cbz", directory)
        var pageCopy: File? = null

        try {
            resolver.openInputStream(source)?.buffered()?.use { input ->
                sourceCopy.outputStream().buffered().use { target ->
                    input.copyToLimited(target, MAX_SOURCE_BYTES, "O arquivo CBR excede o limite de 2 GB.")
                }
            } ?: throw CbrConversionException("Não foi possível ler o arquivo CBR selecionado.")

            Archive(sourceCopy).use { archive ->
                if (archive.isPasswordProtected) {
                    throw CbrConversionException("Arquivos CBR protegidos por senha ainda não são suportados.")
                }
                val headers = archive.fileHeaders
                if (headers.size > MAX_ARCHIVE_ENTRIES) {
                    throw CbrConversionException("O CBR contém entradas demais para ser importado com segurança.")
                }
                val images = headers.asSequence()
                    .filter { !it.isDirectory }
                    .filter { it.fileName.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    .toList()
                    .sortedWith(compareBy { naturalSortKey(it.fileName) })
                if (images.isEmpty()) throw CbrConversionException("Este CBR não contém imagens compatíveis.")
                if (images.size > MAX_ARCHIVE_ENTRIES) {
                    throw CbrConversionException("O CBR contém páginas demais para ser importado com segurança.")
                }

                var totalBytes = 0L
                ZipOutputStream(output.outputStream().buffered()).use { zip ->
                    images.forEachIndexed { index, header ->
                        coroutineContext.ensureActive()
                        val declaredSize = header.fullUnpackSize
                        if (declaredSize < 0 || declaredSize > MAX_PAGE_BYTES) {
                            throw CbrConversionException("Uma página do CBR excede o limite de 80 MB.")
                        }
                        if (totalBytes + declaredSize > MAX_TOTAL_BYTES) {
                            throw CbrConversionException("O conteúdo do CBR excede o limite de 2 GB.")
                        }

                        pageCopy = File.createTempFile("cbr-page-", ".image", context.cacheDir)
                        val checksum = CRC32()
                        CheckedOutputStream(
                            LimitedOutputStream(pageCopy!!.outputStream().buffered(), MAX_PAGE_BYTES),
                            checksum,
                        ).use { pageOutput -> archive.extractFile(header, pageOutput) }

                        val actualSize = pageCopy!!.length()
                        totalBytes += actualSize
                        if (totalBytes > MAX_TOTAL_BYTES) {
                            throw CbrConversionException("O conteúdo do CBR excede o limite de 2 GB.")
                        }
                        val extension = detectComicImageExtension(pageCopy!!)
                            ?: throw CbrConversionException("O CBR contém uma página com formato de imagem inválido.")
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(pageCopy!!.absolutePath, bounds)
                        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                            throw CbrConversionException("O CBR contém uma página de imagem corrompida.")
                        }
                        val entry = ZipEntry(String.format(Locale.ROOT, "%05d.%s", index + 1, extension)).apply {
                            method = ZipEntry.STORED
                            size = actualSize
                            compressedSize = actualSize
                            crc = checksum.value
                        }
                        zip.putNextEntry(entry)
                        pageCopy!!.inputStream().buffered().use { it.copyTo(zip) }
                        zip.closeEntry()
                        pageCopy!!.delete()
                        pageCopy = null
                    }
                }
            }

            if (destination.exists() && !destination.delete()) {
                throw CbrConversionException("Não foi possível substituir uma conversão anterior.")
            }
            if (!output.renameTo(destination)) {
                throw CbrConversionException("Não foi possível concluir a conversão para CBZ.")
            }
            ConvertedComic(Uri.fromFile(destination), destination.length())
        } catch (error: CancellationException) {
            throw error
        } catch (error: CbrConversionException) {
            throw error
        } catch (error: Exception) {
            throw CbrConversionException("Não foi possível converter o CBR. Verifique se o arquivo é válido e não está protegido por senha.", error)
        } finally {
            pageCopy?.delete()
            sourceCopy.delete()
            output.delete()
        }
    }

    fun deleteConverted(uri: String) {
        val parsed = Uri.parse(uri)
        if (parsed.scheme != ContentResolver.SCHEME_FILE) return
        val file = parsed.path?.let(::File)?.canonicalFile ?: return
        if (file.parentFile == conversionDirectory().canonicalFile) file.delete()
    }

    private fun destination(source: Uri, displayName: String): File {
        val base = safeCbzBaseName(displayName)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toString().toByteArray(Charsets.UTF_8))
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(conversionDirectory(), "$base-$digest.cbz")
    }

    private fun conversionDirectory() = File(context.filesDir, CONVERSION_DIRECTORY)

    private companion object {
        const val CONVERSION_DIRECTORY = "converted-comics"
        const val MAX_ARCHIVE_ENTRIES = 10_000
        const val MAX_PAGE_BYTES = 80L * 1024 * 1024
        const val MAX_SOURCE_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}

internal fun safeCbzBaseName(displayName: String): String {
    val source = displayName.substringBeforeLast('.', displayName).trim()
    return source.replace(Regex("[^\\p{L}\\p{N}._-]+"), "-")
        .trim('-', '.', '_')
        .take(80)
        .ifBlank { "quadrinho" }
}

internal fun detectComicImageExtension(file: File): String? = file.inputStream().use { input ->
    val bytes = ByteArray(12)
    val count = input.read(bytes)
    detectComicImageExtension(bytes.copyOf(count.coerceAtLeast(0)))
}

internal fun detectComicImageExtension(bytes: ByteArray): String? = when {
    bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "jpg"
    bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "png"
    bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "webp"
    else -> null
}

private fun naturalSortKey(name: String): String =
    name.lowercase().replace(Regex("\\d+")) { it.value.padStart(12, '0') }

private fun java.io.InputStream.copyToLimited(output: OutputStream, limit: Long, message: String) {
    LimitedOutputStream(output, limit, message).use { limited -> copyTo(limited) }
}

internal class LimitedOutputStream(
    output: OutputStream,
    private val limit: Long,
    private val limitMessage: String = "O conteúdo extraído excede o limite permitido.",
) : FilterOutputStream(output) {
    private var written = 0L

    override fun write(value: Int) {
        requireCapacity(1)
        out.write(value)
        written++
    }

    override fun write(buffer: ByteArray, offset: Int, length: Int) {
        requireCapacity(length)
        out.write(buffer, offset, length)
        written += length
    }

    private fun requireCapacity(increment: Int) {
        if (increment < 0 || written > limit - increment) throw CbrConversionException(limitMessage)
    }
}
