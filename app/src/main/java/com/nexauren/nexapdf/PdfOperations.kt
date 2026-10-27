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
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
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
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import java.util.UUID

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

    fun pageCount(context: Context, uri: Uri, password: String? = null): Int {
        val file = copyUriToCache(context, uri)
        return try {
            openPdfDocument(file, password).use { document -> document.numberOfPages }
        } finally {
            file.delete()
        }
    }

    fun renderPage(context: Context, uri: Uri, pageIndex: Int, password: String? = null, maxWidth: Int = 1200): Bitmap {
        val file = copyUriToCache(context, uri)
        return try {
            openPdfDocument(file, password).use { document ->
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                val safeIndex = pageIndex.coerceIn(0, document.numberOfPages - 1)
                val cropBox = document.getPage(safeIndex).cropBox
                require(maxWidth > 0) { "Preview width must be positive." }
                val longestSide = maxOf(cropBox.width, cropBox.height).coerceAtLeast(1f)
                // Cap the longest bitmap dimension instead of forcing a large minimum scale.
                val scale = min(maxWidth.toFloat() / longestSide, 2f).coerceAtLeast(0.001f)
                PDFRenderer(document).renderImage(safeIndex, scale)
            }
        } finally {
            file.delete()
        }
    }

    private fun protectDocumentCopy(document: PDDocument, password: String) {
        val ownerPassword = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        val policy = StandardProtectionPolicy(ownerPassword, password, AccessPermission())
        policy.setEncryptionKeyLength(128)
        document.protect(policy)
    }

    private fun openPdfDocument(file: File, password: String?): PDDocument {
        return try {
            PDDocument.load(file, password.orEmpty(), MemoryUsageSetting.setupTempFileOnly())
        } catch (error: Exception) {
            val message = error.message.orEmpty().lowercase()
            if (error.javaClass.simpleName.contains("InvalidPassword", ignoreCase = true) ||
                message.contains("password") || message.contains("decrypt")) {
                throw PdfPasswordRequiredException(error)
            }
            throw error
        }
    }

    fun searchText(context: Context, uri: Uri, query: String, password: String? = null): List<PdfSearchHit> {
        val cleanQuery = query.trim()
        require(cleanQuery.isNotEmpty()) { "Enter text to search for." }
        prepare(context)
        val input = copyUriToCache(context, uri)
        try {
            return openPdfDocument(input, password).use { document ->
                val hits = mutableListOf<PdfSearchHit>()
                for (index in 0 until document.numberOfPages) {
                    val stripper = PDFTextStripper().apply {
                        startPage = index + 1
                        endPage = index + 1
                    }
                    val pageText = stripper.getText(document)
                    val snippet = findPdfSearchSnippet(pageText, cleanQuery)
                    if (snippet != null) {
                        hits += PdfSearchHit(pageNumber = index + 1, snippet = snippet)
                        if (hits.size >= 100) break
                    }
                }
                hits
            }
        } finally {
            input.delete()
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

    fun removePageRange(
        context: Context,
        uri: Uri,
        firstPage: Int,
        lastPage: Int,
        password: String? = null
    ): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_removed_", ".pdf", context.cacheDir)
        try {
            openPdfDocument(input, password).use { source ->
                val range = PageRange.of(firstPage, lastPage, source.numberOfPages)
                require(range.pageCount < source.numberOfPages) {
                    "You cannot remove every page from a PDF."
                }
                PDDocument().use { destination ->
                    for (index in 0 until source.numberOfPages) {
                        val pageNumber = index + 1
                        if (pageNumber !in range.firstPage..range.lastPage) {
                            destination.importPage(source.getPage(index))
                        }
                    }
                    if (source.isEncrypted && !password.isNullOrBlank()) protectDocumentCopy(destination, password)
                    destination.save(output)
                }
            }
            require(output.exists() && output.length() > 0L) { "The edited PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun reorderPages(
        context: Context,
        uri: Uri,
        pageOrder: List<Int>,
        password: String? = null
    ): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_reordered_", ".pdf", context.cacheDir)
        try {
            openPdfDocument(input, password).use { source ->
                val order = validatePageOrder(pageOrder, source.numberOfPages)
                PDDocument().use { destination ->
                    order.forEach { pageNumber ->
                        destination.importPage(source.getPage(pageNumber - 1))
                    }
                    if (source.isEncrypted && !password.isNullOrBlank()) protectDocumentCopy(destination, password)
                    destination.save(output)
                }
            }
            require(output.exists() && output.length() > 0L) { "The reordered PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun reorderPages(
        context: Context,
        uri: Uri,
        pageOrderText: String,
        password: String? = null
    ): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_reordered_", ".pdf", context.cacheDir)
        try {
            openPdfDocument(input, password).use { source ->
                val order = parsePageOrder(pageOrderText, source.numberOfPages)
                PDDocument().use { destination ->
                    order.forEach { pageNumber ->
                        destination.importPage(source.getPage(pageNumber - 1))
                    }
                    if (source.isEncrypted && !password.isNullOrBlank()) protectDocumentCopy(destination, password)
                    destination.save(output)
                }
            }
            require(output.exists() && output.length() > 0L) { "The reordered PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    fun rotateAllPages(context: Context, uri: Uri, password: String? = null): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_rotated_", ".pdf", context.cacheDir)
        try {
            openPdfDocument(input, password).use { document ->
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                val wasEncrypted = document.isEncrypted
                for (page in document.pages) page.rotation = (page.rotation + 90) % 360
                if (wasEncrypted && !password.isNullOrBlank()) protectDocumentCopy(document, password)
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

    fun protectWithPassword(context: Context, uri: Uri, password: String): File {
        validatePdfPassword(password)
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_protected_", ".pdf", context.cacheDir)
        try {
            PDDocument.load(input, "", MemoryUsageSetting.setupTempFileOnly()).use { document ->
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                val permissions = AccessPermission().apply {
                    setCanPrint(true)
                    setCanPrintFaithful(true)
                    setCanModify(false)
                    setCanModifyAnnotations(false)
                    setCanExtractContent(false)
                    setCanFillInForm(false)
                    setCanAssembleDocument(false)
                }
                val ownerPassword = UUID.randomUUID().toString() + UUID.randomUUID().toString()
                val policy = StandardProtectionPolicy(ownerPassword, password, permissions)
                policy.setEncryptionKeyLength(128)
                document.protect(policy)
                document.save(output)
            }
            require(output.exists() && output.length() > 0L) { "The protected PDF could not be created." }
            return output
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            input.delete()
        }
    }

    /**
     * Adds an invisible text layer to each upright page using bundled, on-device ML Kit OCR.
     * Original page artwork remains intact. Intended for image/scanned PDFs; already searchable
     * documents can receive duplicate search text, so the UI warns before processing.
     */
    fun createSearchablePdf(context: Context, uri: Uri, password: String? = null): File {
        prepare(context)
        val input = copyUriToCache(context, uri)
        val output = File.createTempFile("nexapdf_searchable_", ".pdf", context.cacheDir)
        var recognizedLines = 0
        try {
            openPdfDocument(input, password).use { document ->
                require(document.numberOfPages > 0) { "This PDF has no pages." }
                val renderer = PDFRenderer(document)
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                try {
                    for (index in 0 until document.numberOfPages) {
                        val page = document.getPage(index)
                        require(page.rotation % 360 == 0) {
                            "OCR currently needs upright pages. Rotate this PDF to 0° and try again."
                        }
                        val crop = page.cropBox
                        val longestSide = maxOf(crop.width, crop.height).coerceAtLeast(1f)
                        val renderScale = min(1.5f, 1800f / longestSide).coerceAtLeast(0.001f)
                        val bitmap = renderer.renderImage(index, renderScale)
                        try {
                            val recognized = Tasks.await(
                                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                            )
                            if (recognized.text.isBlank()) continue
                            val scaleX = crop.width / bitmap.width.toFloat()
                            val scaleY = crop.height / bitmap.height.toFloat()
                            PDPageContentStream(
                                document,
                                page,
                                PDPageContentStream.AppendMode.APPEND,
                                true,
                                true
                            ).use { stream ->
                                for (block in recognized.textBlocks) {
                                    for (line in block.lines) {
                                        val bounds = line.boundingBox ?: continue
                                        val cleanText = normalizeOcrLine(line.text)
                                        if (cleanText.isBlank()) continue
                                        val left = bounds.left.coerceIn(0, bitmap.width)
                                        val bottom = bounds.bottom.coerceIn(0, bitmap.height)
                                        val x = crop.lowerLeftX + left * scaleX
                                        val y = crop.lowerLeftY + crop.height - bottom * scaleY
                                        val fontSize = (bounds.height() * scaleY * 0.78f).coerceIn(4f, 12f)
                                        stream.beginText()
                                        stream.setFont(PDType1Font.HELVETICA, fontSize)
                                        stream.setRenderingMode(RenderingMode.NEITHER)
                                        stream.newLineAtOffset(x, y)
                                        stream.showText(cleanText)
                                        stream.endText()
                                        recognizedLines++
                                    }
                                }
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    require(recognizedLines > 0) {
                        "No text was recognized. Use a clearer scan or a document with upright pages."
                    }
                    document.save(output)
                } finally {
                    recognizer.close()
                }
            }
            require(output.exists() && output.length() > 0L) { "The searchable PDF could not be created." }
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
                val bitmap = decodeSampledBitmap(context, uri, maxDimension = 2200)
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
        } catch (error: Exception) {
            output.delete()
            throw error
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
        .trim()
    val safe = buildString {
        var offset = 0
        while (offset < normalized.length) {
            val codePoint = normalized.codePointAt(offset)
            if (codePoint in 32..126 || codePoint in 160..255) {
                append(codePoint.toChar())
            } else {
                append('?')
            }
            offset += Character.charCount(codePoint)
        }
    }.trim()
    require(safe.isNotBlank()) { "Enter text to add to the PDF." }
    return safe.take(180)
}


internal fun parsePageOrder(input: String, pageCount: Int): List<Int> {
    require(pageCount > 0) { "The PDF has no pages." }
    val values = input.split(',').map { token ->
        token.trim().toIntOrNull()
            ?: throw IllegalArgumentException("Use a comma-separated list of page numbers.")
    }
    return validatePageOrder(values, pageCount)
}

internal fun validatePageOrder(values: List<Int>, pageCount: Int): List<Int> {
    require(pageCount > 0) { "The PDF has no pages." }
    require(values.size == pageCount) {
        "Enter every page number exactly once (1 to " + pageCount + ")."
    }
    require(values.all { it in 1..pageCount }) {
        "Page numbers must be between 1 and " + pageCount + "."
    }
    require(values.toSet().size == pageCount) {
        "Each page number must appear exactly once."
    }
    return values.toList()
}


data class PdfSearchHit(val pageNumber: Int, val snippet: String)

internal fun findPdfSearchSnippet(pageText: String, query: String): String? {
    val search = query.trim()
    if (search.isEmpty()) return null
    val normalizedText = pageText.replace(Regex("\\s+"), " ").trim()
    val index = normalizedText.indexOf(search, ignoreCase = true)
    if (index < 0) return null
    val start = (index - 48).coerceAtLeast(0)
    val end = (index + search.length + 72).coerceAtMost(normalizedText.length)
    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < normalizedText.length) "…" else ""
    return prefix + normalizedText.substring(start, end) + suffix
}


/** Decode selected images with bounded dimensions so huge phone photos don't exhaust memory. */
private fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    require(maxDimension > 0) { "Maximum image dimension must be positive." }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val firstStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("The selected image could not be opened.")
    firstStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected file is not a supported image." }

    var sampleSize = 1
    while (maxOf(bounds.outWidth / sampleSize, bounds.outHeight / sampleSize) > maxDimension && sampleSize < (1 shl 20)) {
        sampleSize *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val secondStream = context.contentResolver.openInputStream(uri)
        ?: throw IllegalArgumentException("The selected image could not be opened.")
    return secondStream.use { BitmapFactory.decodeStream(it, null, options) }
        ?: throw IllegalArgumentException("The selected image could not be decoded.")
}


/** Normalize OCR text for PDFBox's built-in WinAnsi Helvetica font. */
internal fun normalizeOcrLine(raw: String): String {
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
        .replace('\u2022', '-')
        .trim()
    val output = StringBuilder()
    var offset = 0
    while (offset < normalized.length) {
        val codePoint = normalized.codePointAt(offset)
        if ((codePoint in 32..126) || (codePoint in 160..255)) {
            output.append(codePoint.toChar())
        } else {
            output.append('?')
        }
        offset += Character.charCount(codePoint)
    }
    return output.toString().take(400)
}


internal class PdfPasswordRequiredException(cause: Throwable) : Exception("This PDF requires a valid password.", cause)

internal fun validatePdfPassword(password: String) {
    require(password.length >= 8) { "Use at least 8 characters for the PDF password." }
    require(password.isNotBlank()) { "Password cannot be blank." }
}
