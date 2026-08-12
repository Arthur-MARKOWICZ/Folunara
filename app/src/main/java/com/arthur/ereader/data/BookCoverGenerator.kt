package com.arthur.ereader.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Size
import com.arthur.ereader.core.files.FormatTools
import com.arthur.ereader.domain.model.Book
import com.arthur.ereader.domain.model.BookFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

@Singleton
class BookCoverGenerator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: ContentResolver,
) {
    suspend fun generate(book: Book): String? = withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            when (book.format) {
                BookFormat.EPUB -> epubCover(book)
                BookFormat.PDF -> pdfCover(book)
                BookFormat.CBZ -> cbzCover(book)
            }
        }.getOrNull() ?: return@withContext null

        try {
            val directory = File(context.filesDir, COVER_DIRECTORY).apply { mkdirs() }
            val target = File(directory, "${book.id}.jpg")
            val flattened = flatten(bitmap)
            try {
                target.outputStream().buffered().use { output ->
                    check(flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
                }
            } finally {
                if (flattened !== bitmap) flattened.recycle()
            }
            target.toURI().toString()
        } finally {
            bitmap.recycle()
        }
    }

    fun delete(bookId: Long) {
        File(File(context.filesDir, COVER_DIRECTORY), "$bookId.jpg").delete()
    }

    private fun pdfCover(book: Book): Bitmap? =
        resolver.openFileDescriptor(Uri.parse(book.uri), "r")?.use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                if (renderer.pageCount == 0) return@use null
                renderer.openPage(0).use { page ->
                    val (width, height) = fitSize(page.width, page.height)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }

    private suspend fun epubCover(book: Book): Bitmap? {
        val cached = File.createTempFile("cover-${book.id}-", ".epub", context.cacheDir)
        return try {
            resolver.openInputStream(Uri.parse(book.uri))?.use { input ->
                cached.outputStream().use(input::copyTo)
            } ?: return null
            val asset = AssetRetriever(resolver, DefaultHttpClient()).retrieve(cached).getOrNull() ?: return null
            val publication = PublicationOpener(EpubParser()).open(asset, allowUserInteraction = false).getOrNull()
                ?: return null
            try {
                publication.coverFitting(Size(MAX_WIDTH, MAX_HEIGHT))
            } finally {
                publication.close()
            }
        } finally {
            cached.delete()
        }
    }

    private fun cbzCover(book: Book): Bitmap? {
        val cached = File.createTempFile("cover-${book.id}-", ".cbz", context.cacheDir)
        return try {
            resolver.openInputStream(Uri.parse(book.uri))?.use { input ->
                cached.outputStream().use(input::copyTo)
            } ?: return null
            ZipFile(cached).use { zip ->
                val names = zip.entries().asSequence()
                    .filter { !it.isDirectory && it.size <= MAX_ARCHIVE_IMAGE_BYTES }
                    .map { it.name }
                    .filter { it.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
                    .toList()
                    .let(FormatTools::naturalSort)
                val entry = names.firstNotNullOfOrNull(zip::getEntry) ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, bounds) }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val sample = sampleSize(bounds.outWidth, bounds.outHeight)
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                zip.getInputStream(entry).use { BitmapFactory.decodeStream(it, null, options) }
                    ?.scaledToFit()
            }
        } finally {
            cached.delete()
        }
    }

    private fun Bitmap.scaledToFit(): Bitmap {
        val (targetWidth, targetHeight) = fitSize(width, height)
        if (targetWidth == width && targetHeight == height) return this
        val scaled = Bitmap.createScaledBitmap(this, targetWidth, targetHeight, true)
        recycle()
        return scaled
    }

    private fun flatten(source: Bitmap): Bitmap {
        if (!source.hasAlpha()) return source
        return Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888).also { result ->
            Canvas(result).apply {
                drawColor(Color.rgb(255, 249, 234))
                drawBitmap(source, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
        }
    }

    private fun fitSize(width: Int, height: Int): Pair<Int, Int> {
        return fittedCoverSize(width, height, MAX_WIDTH, MAX_HEIGHT)
    }

    private fun sampleSize(width: Int, height: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > MAX_DECODE_EDGE) sample *= 2
        return sample
    }

    private companion object {
        const val COVER_DIRECTORY = "book-covers"
        const val MAX_WIDTH = 600
        const val MAX_HEIGHT = 900
        const val MAX_DECODE_EDGE = 1800
        const val JPEG_QUALITY = 88
        const val MAX_ARCHIVE_IMAGE_BYTES = 80L * 1024 * 1024
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    }
}

internal fun fittedCoverSize(width: Int, height: Int, maxWidth: Int = 600, maxHeight: Int = 900): Pair<Int, Int> {
    require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
    val scale = minOf(1f, maxWidth.toFloat() / width, maxHeight.toFloat() / height)
    return (width * scale).roundToInt().coerceAtLeast(1) to
        (height * scale).roundToInt().coerceAtLeast(1)
}
