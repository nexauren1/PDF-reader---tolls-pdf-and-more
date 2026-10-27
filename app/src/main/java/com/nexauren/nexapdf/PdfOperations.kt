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
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/** Local-first PDF operations. Files stay in app cache until the user chooses where to save them. */
object PdfOperations {
    fun prepare(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
        // Remove abandoned temporary files from earlier sessions; never touch user-picked documents.
        val cutoff = System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        context.cacheDir.listFiles()
            ?.filter { it.name.startsWith("nexapdf_") && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
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
        return try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { pfd -> PdfRenderer(pfd).use { renderer -> renderer.pageCount } }
        } finally {
            file.delete()
        }
    }

    fun renderPage(context: Context, uri: Uri, pageIndex: Int, maxWidth: Int = 1200): Bitmap {
        val file = copyUriToCache(context, uri)
        return try {
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
                        val transform = android.graphics.Matrix().apply {
                            setScale(width.toFloat() / page.width, height.toFloat() / page.height)
                        }
                        page.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    fun merge(context: Context, uris: List<Uri>): File {
        require(uris.size >= 2) { "Select at least two PDF files to merge." }
        prepare(context)
        val output = File.createTempFile("nexapdf_merged_", ".pdf", context.cacheDir)
        val inputs = mutableListOf<File>()
        try {
            uris.forEach { inputs += copyUriToCache(context, it) }
            val utility = PDFMergerUtility()
            inputs.forEach { utility.addSource(it) }
            utility.destinationFileName = output.absolutePath
            utility.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
            require(output.exists() && output.length() > 0L) { "The merged PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            inputs.forEach { it.delete() }
        }
    }

    fun split(context: Context, uri: Uri, firstPage: Int, lastPage: Int): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_pages_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(input).use { source ->
                val range = PageRange.of(firstPage, lastPage, source.numberOfPages)
                PDDocument().use { destination ->
                    for (index in (range.firstPage - 1) until range.lastPage) {
                        destination.importPage(source.getPage(index))
                    }
                    destination.save(output)
                }
            }
            require(output.length() > 0L) { "The selected pages could not be exported." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun rotateAllPages(context: Context, uri: Uri): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_rotated_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(input).use { document ->
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                for (page in document.pages) page.rotation = (page.rotation + 90) % 360
                document.save(output)
            }
            require(output.length() > 0L) { "The rotated PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    /**
     * Produces a smaller, image-based PDF. This intentionally rasterizes each page:
     * searchable/selectable text, hyperlinks, forms and vector content will not be preserved.
     * The source is never modified; the caller must clearly warn the user before running it.
     */
    fun compressRasterizedPdf(
        context: Context,
        uri: Uri,
        maxPageDimension: Int,
        jpegQuality: Int
    ): File {
        require(maxPageDimension in 700..2200) { "Choose a page size between 700 and 2200 pixels." }
        require(jpegQuality in 45..92) { "Choose JPEG quality between 45 and 92." }
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_compressed_", ".pdf", context.cacheDir)
        try {
            val descriptor = ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor.use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    require(renderer.pageCount > 0) { "This PDF has no pages." }
                    PDDocument().use { destination ->
                        for (index in 0 until renderer.pageCount) {
                            renderer.openPage(index).use { sourcePage ->
                                val longestSide = maxOf(sourcePage.width, sourcePage.height).coerceAtLeast(1)
                                val scale = maxPageDimension.toFloat() / longestSide.toFloat()
                                val pixelWidth = (sourcePage.width * scale).toInt().coerceAtLeast(1)
                                val pixelHeight = (sourcePage.height * scale).toInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(pixelWidth, pixelHeight, Bitmap.Config.ARGB_8888)
                                val bytes = ByteArrayOutputStream()
                                try {
                                    bitmap.eraseColor(Color.WHITE)
                                    val transform = android.graphics.Matrix().apply {
                                        setScale(pixelWidth.toFloat() / sourcePage.width, pixelHeight.toFloat() / sourcePage.height)
                                    }
                                    sourcePage.render(bitmap, null, transform, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    val encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, bytes)
                                    require(encoded && bytes.size() > 0) { "Page " + (index + 1) + " could not be compressed." }
                                } finally {
                                    bitmap.recycle()
                                }

                                val outputPage = PDPage(PDRectangle(sourcePage.width.toFloat(), sourcePage.height.toFloat()))
                                destination.addPage(outputPage)
                                val image = PDImageXObject.createFromByteArray(destination, bytes.toByteArray(), "compressed-page-" + (index + 1))
                                PDPageContentStream(destination, outputPage).use { stream ->
                                    stream.drawImage(image, 0f, 0f, outputPage.mediaBox.width, outputPage.mediaBox.height)
                                }
                            }
                        }
                        destination.save(output)
                    }
                }
            }
            require(output.exists() && output.length() > 0L) { "The compressed PDF could not be created." }
            require(output.length() < input.length()) {
                "This PDF did not become smaller with the selected quality. Try the Smallest preset."
            }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun addTextAnnotation(context: Context, uri: Uri, pageNumber: Int, rawText: String): File {
        prepare(context)
        val text = normalizePdfAnnotationText(rawText)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_annotated_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(input).use { document ->
                require(pageNumber in 1..document.numberOfPages) {
                    "Page number must be between 1 and " + document.numberOfPages + "."
                }
                val page = document.getPage(pageNumber - 1)
                val box = page.mediaBox
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 12f)
                    stream.setNonStrokingColor(115, 87, 246)
                    stream.newLineAtOffset(box.lowerLeftX + 36f, box.lowerLeftY + 36f)
                    stream.showText(text)
                    stream.endText()
                }
                document.save(output)
            }
            require(output.exists() && output.length() > 0L) { "The annotated PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun addSignature(context: Context, uri: Uri, pageNumber: Int, signature: Bitmap): File {
        prepare(context)
        require(signature.width > 0 && signature.height > 0) { "The signature image is empty." }
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_signed_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(input).use { document ->
                require(pageNumber in 1..document.numberOfPages) {
                    "Page number must be between 1 and " + document.numberOfPages + "."
                }
                val page = document.getPage(pageNumber - 1)
                val box = page.mediaBox
                val availableWidth = (box.width - 72f).coerceAtLeast(80f)
                val drawWidth = min(180f, availableWidth)
                val drawHeight = drawWidth * signature.height.toFloat() / signature.width.toFloat()
                val image = LosslessFactory.createFromImage(document, signature)
                PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true
                ).use { stream ->
                    stream.drawImage(
                        image,
                        box.lowerLeftX + 36f,
                        box.lowerLeftY + 36f,
                        drawWidth,
                        drawHeight.coerceAtMost(60f)
                    )
                }
                document.save(output)
            }
            require(output.exists() && output.length() > 0L) { "The signed PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
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


/** Clean single-line text for a PDF text operator using the built-in WinAnsi Helvetica font. */
internal fun normalizePdfAnnotationText(raw: String): String {
    val normalized = raw
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u201C', '"')
        .replace('\u201D', '"')
        .replace('\u2013', '-')
        .replace('\u2014', '-')
        .filter { it >= ' ' && it != '\u007f' }
        .trim()
    require(normalized.isNotBlank()) { "Enter text to add to the PDF." }
    return normalized.take(180)
}
