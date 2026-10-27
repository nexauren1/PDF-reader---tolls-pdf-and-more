package com.nexauren.nexapdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Local-first PDF operations. Files stay in app cache until the user chooses where to save them. */
object PdfOperations {
    fun prepare(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun displayName(context: Context, uri: Uri): String {
        var name: String? = null
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        return name?.takeIf { it.isNotBlank() } ?: (uri.lastPathSegment ?: "document.pdf")
    }

    fun copyUriToCache(context: Context, uri: Uri, suffix: String = ".pdf"): File {
        val result = File.createTempFile("nexapdf_input_", suffix, context.cacheDir)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Android could not open this file.")
        input.use { source -> FileOutputStream(result).use { target -> source.copyTo(target) } }
        if (result.length() == 0L) throw IllegalArgumentException("The selected file is empty.")
        return result
    }

    fun pageCount(context: Context, uri: Uri): Int {
        val file = copyUriToCache(context, uri)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return descriptor.use { pfd -> PdfRenderer(pfd).use { renderer -> renderer.pageCount } }
    }

    fun renderPage(context: Context, uri: Uri, pageIndex: Int, maxWidth: Int = 1200): Bitmap {
        val file = copyUriToCache(context, uri)
        val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                require(renderer.pageCount > 0) { "This PDF has no pages." }
                val safeIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safeIndex).use { page ->
                    val width = min(page.width, maxWidth).coerceAtLeast(1)
                    val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap
                }
            }
        }
    }

    fun merge(context: Context, uris: List<Uri>): File {
        require(uris.size >= 2) { "Select at least two PDF files to merge." }
        prepare(context)
        val output = File.createTempFile("nexapdf_merged_", ".pdf", context.cacheDir)
        val utility = PDFMergerUtility()
        val inputs = uris.map { copyUriToCache(context, it) }
        inputs.forEach { utility.addSource(it) }
        utility.destinationFileName = output.absolutePath
        utility.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        require(output.exists() && output.length() > 0L) { "The merged PDF could not be created." }
        return output
    }

    fun split(context: Context, uri: Uri, firstPage: Int, lastPage: Int): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_pages_", ".pdf", context.cacheDir)
        PDDocument.load(input).use { source ->
            val count = source.numberOfPages
            val range = PageRange.of(firstPage, lastPage, count)
            PDDocument().use { destination ->
                for (index in (range.firstPage - 1) until range.lastPage) {
                    destination.importPage(source.getPage(index))
                }
                destination.save(output)
            }
        }
        require(output.length() > 0L) { "The selected pages could not be exported." }
        return output
    }

    fun rotateAllPages(context: Context, uri: Uri): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_rotated_", ".pdf", context.cacheDir)
        PDDocument.load(input).use { document ->
            require(document.numberOfPages > 0) { "This PDF has no pages." }
            for (page in document.pages) page.rotation = (page.rotation + 90) % 360
            document.save(output)
        }
        require(output.length() > 0L) { "The rotated PDF could not be created." }
        return output
    }

    /** Converts selected images to a real PDF using Android's built-in PdfDocument API. */
    fun imagesToPdf(context: Context, uris: List<Uri>): File {
        require(uris.isNotEmpty()) { "Select at least one image." }
        val output = File.createTempFile("nexapdf_images_", ".pdf", context.cacheDir)
        val pdf = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalArgumentException("An image could not be read.")
                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(pageInfo)
                    val canvas: Canvas = page.canvas
                    canvas.drawColor(Color.WHITE)
                    val maxW = pageWidth - 48f
                    val maxH = pageHeight - 48f
                    val scale = min(maxW / bitmap.width, maxH / bitmap.height)
                    val drawnW = bitmap.width * scale
                    val drawnH = bitmap.height * scale
                    val left = (pageWidth - drawnW) / 2f
                    val top = (pageHeight - drawnH) / 2f
                    canvas.drawBitmap(bitmap, null, RectF(left, top, left + drawnW, top + drawnH), paint)
                    pdf.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(output).use { pdf.writeTo(it) }
        } finally {
            pdf.close()
        }
        require(output.exists() && output.length() > 0L) { "The PDF could not be created." }
        return output
    }
}
