package com.arthur.ereader.reader.pdf

import android.content.ContentResolver
import android.content.Context
import androidx.core.net.toUri
import com.arthur.ereader.domain.model.Book
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfReflowRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
    private val extractor: AndroidxPdfContentExtractor,
) {
    private val mutex = Mutex()
    private val memory = mutableMapOf<String, PdfReflowDocument>()

    suspend fun load(book: Book, force: Boolean = false): Result<PdfReflowDocument> = runCatching {
        withContext(Dispatchers.IO) {
            val fingerprint = fingerprint(book)
            val key = "${extractor.version}-$fingerprint"
            mutex.withLock {
                if (!force) memory[key]?.let { return@withLock it }
                val file = File(cacheDirectory(), "$key.bin")
                if (!force && file.isFile) {
                    runCatching { read(file) }.getOrNull()?.let {
                        memory[key] = it
                        return@withLock it
                    }
                }
                val extracted = extractor.extract(book, fingerprint)
                write(file, extracted)
                memory[key] = extracted
                extracted
            }
        }
    }

    private fun cacheDirectory() = File(context.cacheDir, "pdf-reflow").apply { mkdirs() }

    private fun fingerprint(book: Book): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(book.uri.toUri())?.use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Não foi possível ler o PDF.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun write(file: File, document: PdfReflowDocument) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        DataOutputStream(BufferedOutputStream(temporary.outputStream())).use { output ->
            output.writeInt(CACHE_MAGIC)
            output.writeInt(document.extractorVersion)
            output.writeLong(document.bookId)
            output.writeUTF(document.title.take(MAX_UTF_CHARS))
            output.writeUTF(document.fingerprint)
            output.writeInt(document.pages.size)
            document.pages.forEach { page ->
                output.writeInt(page.index)
                output.writeInt(page.width)
                output.writeInt(page.height)
                output.writeUTF(page.classification.name)
                output.writeBoolean(page.diagnostics.textBeyondPage)
                output.writeBoolean(page.diagnostics.textTouchesSafetyMargin)
                output.writeBoolean(page.diagnostics.hasImages)
                output.writeBoolean(page.diagnostics.suspectedColumns)
                output.writeInt(page.diagnostics.reasons.size)
                page.diagnostics.reasons.forEach { output.writeUTF(it.take(MAX_UTF_CHARS)) }
                output.writeInt(page.blocks.size)
                page.blocks.forEach { block ->
                    output.writeUTF(block.text.take(MAX_UTF_CHARS))
                    output.writeInt(block.bounds.size)
                    block.bounds.forEach { bounds ->
                        output.writeFloat(bounds.left)
                        output.writeFloat(bounds.top)
                        output.writeFloat(bounds.right)
                        output.writeFloat(bounds.bottom)
                    }
                }
            }
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun read(file: File): PdfReflowDocument =
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            require(input.readInt() == CACHE_MAGIC)
            val version = input.readInt()
            val bookId = input.readLong()
            val title = input.readUTF()
            val fingerprint = input.readUTF()
            val pages = List(input.readInt().checkedCount()) {
                val index = input.readInt()
                val width = input.readInt()
                val height = input.readInt()
                val classification = PdfPageClassification.valueOf(input.readUTF())
                val beyond = input.readBoolean()
                val touches = input.readBoolean()
                val images = input.readBoolean()
                val columns = input.readBoolean()
                val reasons = List(input.readInt().checkedCount()) { input.readUTF() }
                val blocks = List(input.readInt().checkedCount()) {
                    val text = input.readUTF()
                    val bounds = List(input.readInt().checkedCount()) {
                        PdfTextBounds(input.readFloat(), input.readFloat(), input.readFloat(), input.readFloat())
                    }
                    PdfTextBlock(text, bounds)
                }
                PdfExtractedPage(
                    index,
                    width,
                    height,
                    blocks,
                    classification,
                    PdfPageDiagnostics(width, height, beyond, touches, images, columns, reasons),
                )
            }
            PdfReflowDocument(bookId, title, fingerprint, version, pages)
        }

    private fun Int.checkedCount(): Int = also { require(it in 0..MAX_CACHE_ITEMS) }

    private companion object {
        const val CACHE_MAGIC = 0x50524631
        const val MAX_CACHE_ITEMS = 100_000
        const val MAX_UTF_CHARS = 15_000
    }
}
