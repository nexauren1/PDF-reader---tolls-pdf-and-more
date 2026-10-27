package com.nexauren.nexapdf

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import java.io.File

private val AppBg = Color(0xFF07111F)
private val Panel = Color(0xFF101E31)
private val PanelAlt = Color(0xFF14263D)
private val SecondaryText = Color(0xFF9DAEC5)
private val Violet = Color(0xFF7357F6)
private val Cyan = Color(0xFF42D6C5)
private val Blue = Color(0xFF269CF5)
private val Orange = Color(0xFFFF8B4D)
private val Pink = Color(0xFFFF5E91)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 17, 31)
        window.navigationBarColor = android.graphics.Color.rgb(7, 17, 31)
        PdfOperations.prepare(this)
        setContent { NexaPdfApp() }
    }
}

private data class Recent(val uri: String, val name: String)
private data class PdfTool(
    val id: String, val pt: String, val en: String, val detailPt: String, val detailEn: String,
    val color: Color, val icon: ImageVector, val ready: Boolean = false
)

private val tools = listOf(
    PdfTool("merge", "Juntar PDF", "Merge PDF", "Combina vários ficheiros num só", "Combine several files into one", Violet, Icons.Filled.Description, true),
    PdfTool("split", "Dividir PDF", "Split PDF", "Extrai páginas por intervalo", "Extract pages by range", Blue, Icons.Filled.ContentCut, true),
    PdfTool("image", "Imagem para PDF", "Image to PDF", "Cria um PDF a partir de imagens", "Create a PDF from images", Cyan, Icons.Filled.Image, true),
    PdfTool("rotate", "Rodar páginas", "Rotate pages", "Roda páginas 90° no sentido horário", "Rotate pages 90° clockwise", Orange, Icons.Filled.RotateRight, true),
    PdfTool("compress", "Comprimir PDF", "Compress PDF", "Reduz o tamanho do documento", "Reduce document size", Blue, Icons.Filled.Compress, true),
    PdfTool("edit", "Editar e assinar", "Annotate & sign", "Adiciona texto ou assinatura", "Add text or a drawn signature", Pink, Icons.Filled.Edit, true),
    PdfTool("scan", "Digitalizar", "Scan document", "Captura e corrige documentos", "Capture and crop documents", Cyan, Icons.Filled.CameraAlt, true),
    PdfTool("protect", "Proteger PDF", "Protect PDF", "Proteção por palavra-passe", "Password protection", Violet, Icons.Filled.Lock, true),
    PdfTool("ocr", "OCR", "OCR", "Torna digitalizações pesquisáveis", "Make scans searchable", Blue, Icons.Filled.Search),
    PdfTool("translate", "Traduzir PDF", "Translate PDF", "Tradução online opcional", "Optional online translation", Pink, Icons.Filled.Translate)
)

@Composable
private fun NexaPdfApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val prefs = remember { context.getSharedPreferences("nexapdf", android.content.Context.MODE_PRIVATE) }
    var language by remember { mutableStateOf(prefs.getString("language", "pt") ?: "pt") }
    val pt = language == "pt"
    var tab by remember { mutableStateOf("home") }
    var activeUri by remember { mutableStateOf<Uri?>(null) }
    var activeName by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var busy by remember { mutableStateOf(false) }
    var busyText by remember { mutableStateOf("") }
    var pendingFile by remember { mutableStateOf<File?>(null) }
    var query by remember { mutableStateOf("") }
    var splitUri by remember { mutableStateOf<Uri?>(null) }
    var splitCount by remember { mutableIntStateOf(1) }
    var splitStart by remember { mutableStateOf("1") }
    var splitEnd by remember { mutableStateOf("1") }
    var showSplit by remember { mutableStateOf(false) }
    var rotateUri by remember { mutableStateOf<Uri?>(null) }
    var showRotate by remember { mutableStateOf(false) }
    var editUri by remember { mutableStateOf<Uri?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var compressionUri by remember { mutableStateOf<Uri?>(null) }
    var showCompressionDialog by remember { mutableStateOf(false) }
    var protectUri by remember { mutableStateOf<Uri?>(null) }
    var showProtectionDialog by remember { mutableStateOf(false) }
    var passwordUri by remember { mutableStateOf<Uri?>(null) }
    var passwordName by remember { mutableStateOf("") }
    var passwordWasWrong by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val recentDocs = remember {
        mutableStateListOf<Recent>().apply {
            (prefs.getString("recent", "") ?: "").split("\n").filter { it.isNotBlank() }.forEach { row ->
                val parts = row.split("\u001f", limit = 2)
                if (parts.size == 2) add(Recent(parts[0], parts[1]))
            }
        }
    }

    fun rememberRecent(uri: Uri, name: String) {
        recentDocs.removeAll { it.uri == uri.toString() }
        recentDocs.add(0, Recent(uri.toString(), name))
        while (recentDocs.size > 10) recentDocs.removeAt(recentDocs.lastIndex)
        prefs.edit().putString("recent", recentDocs.joinToString("\n") { it.uri + "\u001f" + it.name }).apply()
    }

    fun loadPdf(uri: Uri, index: Int = 0, suppliedName: String? = null, password: String? = null) {
        scope.launch {
            try {
                busy = true
                busyText = if (pt) "A abrir PDF…" else "Opening PDF…"
                val result = withContext(Dispatchers.IO) {
                    val count = PdfOperations.pageCount(context, uri, password)
                    val bitmap = PdfOperations.renderPage(context, uri, index.coerceIn(0, (count - 1).coerceAtLeast(0)), password)
                    Pair(count, bitmap)
                }
                activeUri = uri
                activeName = suppliedName ?: PdfOperations.displayName(context, uri)
                pageCount = result.first
                page = index.coerceIn(0, (result.first - 1).coerceAtLeast(0))
                preview = result.second
                tab = "reader"
                rememberRecent(uri, activeName)
            } catch (e: PdfPasswordRequiredException) {
                passwordUri = uri
                passwordName = suppliedName ?: PdfOperations.displayName(context, uri)
                passwordWasWrong = !password.isNullOrBlank()
                showPasswordDialog = true
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: if (pt) "Não foi possível abrir este PDF." else "Could not open this PDF.")
            } finally { busy = false }
        }
    }

    val openPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            loadPdf(uri)
        }
    }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(50)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember { GmsDocumentScanning.getClient(scannerOptions) }

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { destination ->
        val source = pendingFile
        if (destination != null && source != null) scope.launch {
            try {
                busy = true
                busyText = if (pt) "A guardar PDF…" else "Saving PDF…"
                withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openOutputStream(destination)
                        ?: throw IllegalArgumentException("Could not write to this location.")
                    stream.use { out -> source.inputStream().use { it.copyTo(out) } }
                }
                try {
                    context.contentResolver.takePersistableUriPermission(
                        destination,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                } catch (_: Exception) { }
                val savedName = PdfOperations.displayName(context, destination)
                rememberRecent(destination, savedName)
                snackbar.showSnackbar(if (pt) "PDF guardado com sucesso." else "PDF saved successfully.")
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: if (pt) "Falha ao guardar." else "Could not save file.")
            } finally { busy = false; pendingFile = null }
        }
    }

    fun runOperation(label: String, work: suspend () -> File) {
        scope.launch {
            try {
                busy = true
                busyText = label
                val file = withContext(Dispatchers.IO) { work() }
                pendingFile = file
                savePicker.launch(file.name)
            } catch (e: Exception) {
                snackbar.showSnackbar(e.message ?: if (pt) "A operação falhou." else "The operation failed.")
            } finally { busy = false }
        }
    }

    var mergeCallback by remember { mutableStateOf<(List<Uri>) -> Unit>({}) }
    var imageCallback by remember { mutableStateOf<(List<Uri>) -> Unit>({}) }
    var splitCallback by remember { mutableStateOf<(Uri?) -> Unit>({}) }
    var rotateCallback by remember { mutableStateOf<(Uri?) -> Unit>({}) }

    val mergePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { mergeCallback(it) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { imageCallback(it) }
    val splitPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { splitCallback(it) }
    val rotatePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { rotateCallback(it) }
    var editCallback by remember { mutableStateOf<(Uri?) -> Unit>({}) }
    val editPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { editCallback(it) }
    val compressionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { compressionUri = uri; showCompressionDialog = true }
    }
    val protectionPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { protectUri = uri; showProtectionDialog = true }
    }

    mergeCallback = { uris ->
        if (uris.size >= 2) runOperation(if (pt) "A juntar PDFs…" else "Merging PDFs…") { PdfOperations.merge(context, uris) }
        else if (uris.isNotEmpty()) scope.launch { snackbar.showSnackbar(if (pt) "Seleciona pelo menos dois PDFs." else "Select at least two PDFs.") }
    }
    imageCallback = { uris ->
        if (uris.isNotEmpty()) runOperation(if (pt) "A converter imagens…" else "Converting images…") { PdfOperations.imagesToPdf(context, uris) }
    }
    splitCallback = { uri ->
        if (uri != null) scope.launch {
            try {
                val count = withContext(Dispatchers.IO) { PdfOperations.pageCount(context, uri) }
                splitUri = uri
                splitCount = count
                splitStart = "1"
                splitEnd = count.toString()
                showSplit = true
            } catch (e: Exception) { snackbar.showSnackbar(e.message ?: "Could not read PDF.") }
        }
    }
    rotateCallback = { uri -> if (uri != null) { rotateUri = uri; showRotate = true } }
    editCallback = { uri -> if (uri != null) { editUri = uri; showEditDialog = true } }

    val scanResultLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val pdfUri = result.data
                ?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
                ?.pdf
                ?.uri
            if (pdfUri == null) {
                scope.launch { snackbar.showSnackbar(if (pt) "O scanner não devolveu um PDF." else "The scanner did not return a PDF.") }
            } else {
                scope.launch {
                    try {
                        busy = true
                        busyText = if (pt) "A preparar digitalização…" else "Preparing scan…"
                        val cachedPdf = withContext(Dispatchers.IO) {
                            PdfOperations.copyUriToCache(context, pdfUri)
                        }
                        pendingFile = cachedPdf
                        savePicker.launch("NexaPDF_Scan.pdf")
                    } catch (error: Exception) {
                        snackbar.showSnackbar(error.message ?: if (pt) "Não foi possível preparar a digitalização." else "Could not prepare the scan.")
                    } finally {
                        busy = false
                    }
                }
            }
        } else {
            busy = false
        }
    }

    fun startDocumentScanner() {
        val activity = context as? Activity
        if (activity == null) {
            scope.launch { snackbar.showSnackbar(if (pt) "Não foi possível abrir o scanner neste ecrã." else "Could not launch the scanner from this screen.") }
            return
        }
        busy = true
        busyText = if (pt) "A preparar scanner…" else "Preparing scanner…"
        documentScanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                busy = false
                scanResultLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { error ->
                busy = false
                scope.launch {
                    snackbar.showSnackbar(
                        error.localizedMessage
                            ?: if (pt) "O scanner não está disponível. Verifica os Google Play services." else "Scanner unavailable. Check Google Play services."
                    )
                }
            }
    }

    fun launchTool(id: String) {
        when (id) {
            "open" -> openPicker.launch(arrayOf("application/pdf"))
            "merge" -> mergePicker.launch(arrayOf("application/pdf"))
            "image" -> imagePicker.launch(arrayOf("image/*"))
            "split" -> splitPicker.launch(arrayOf("application/pdf"))
            "rotate" -> rotatePicker.launch(arrayOf("application/pdf"))
            "edit" -> editPicker.launch(arrayOf("application/pdf"))
            "compress" -> compressionPicker.launch(arrayOf("application/pdf"))
            "protect" -> protectionPicker.launch(arrayOf("application/pdf"))
            "scan" -> startDocumentScanner()
            else -> scope.launch {
                snackbar.showSnackbar(if (pt) "Esta ferramenta está em desenvolvimento e ainda não está ativa." else "This tool is in development and is not active yet.")
            }
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Violet, secondary = Cyan, background = AppBg, surface = Panel, onSurface = Color.White)) {
        Scaffold(
            containerColor = AppBg,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (tab != "reader") NavigationBar(containerColor = Color(0xFF091525), contentColor = Color.White) {
                    listOf(
                        Triple("home", if (pt) "Início" else "Home", Icons.Filled.Home),
                        Triple("documents", if (pt) "Documentos" else "Documents", Icons.Filled.FolderOpen),
                        Triple("tools", if (pt) "Ferramentas" else "Tools", Icons.Filled.Description),
                        Triple("settings", if (pt) "Definições" else "Settings", Icons.Filled.Settings)
                    ).forEach { item ->
                        NavigationBarItem(
                            selected = tab == item.first, onClick = { tab = item.first },
                            icon = { Icon(item.third, null) }, label = { Text(item.second, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = Cyan, selectedTextColor = Cyan, indicatorColor = PanelAlt, unselectedIconColor = SecondaryText, unselectedTextColor = SecondaryText)
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    "home" -> HomeScreen(pt, recentDocs, query, { query = it }, { launchTool("open") }, ::launchTool, { loadPdf(Uri.parse(it.uri), 0, it.name) }, { tab = "documents" })
                    "tools" -> ToolsScreen(pt, ::launchTool)
                    "documents" -> DocumentsScreen(pt, recentDocs, { launchTool("open") }, { loadPdf(Uri.parse(it.uri), 0, it.name) })
                    "settings" -> SettingsScreen(pt, { language = it; prefs.edit().putString("language", it).apply() }, { showAbout = true })
                    "reader" -> ReaderScreen(pt, activeName, preview, page, pageCount,
                        { tab = "home" },
                        { activeUri?.let { loadPdf(it, page - 1, activeName) } },
                        { activeUri?.let { loadPdf(it, page + 1, activeName) } },
                        {
                            activeUri?.let { uri ->
                                val share = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(share, if (pt) "Partilhar PDF" else "Share PDF"))
                            }
                        })
                }
            }
        }
    }

    if (showSplit) AlertDialog(
        onDismissRequest = { showSplit = false }, containerColor = Panel,
        title = { Text(if (pt) "Dividir PDF" else "Split PDF", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((if (pt) "O PDF tem " else "This PDF has ") + splitCount + (if (pt) " páginas." else " pages."), color = SecondaryText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(splitStart, { splitStart = it.filter(Char::isDigit).take(5) }, label = { Text(if (pt) "Página inicial" else "First page") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(splitEnd, { splitEnd = it.filter(Char::isDigit).take(5) }, label = { Text(if (pt) "Página final" else "Last page") }, modifier = Modifier.weight(1f), singleLine = true)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val first = splitStart.toIntOrNull()
                val last = splitEnd.toIntOrNull()
                val uri = splitUri
                if (uri == null || first == null || last == null || first < 1 || last < first || last > splitCount) {
                    scope.launch { snackbar.showSnackbar(if (pt) "Intervalo inválido." else "Invalid page range.") }
                } else {
                    showSplit = false
                    runOperation(if (pt) "A extrair páginas…" else "Extracting pages…") { PdfOperations.split(context, uri, first, last) }
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (pt) "Extrair e guardar" else "Extract and save") }
        },
        dismissButton = { TextButton(onClick = { showSplit = false }) { Text(if (pt) "Cancelar" else "Cancel") } }
    )

    if (showRotate) AlertDialog(
        onDismissRequest = { showRotate = false }, containerColor = Panel,
        title = { Text(if (pt) "Rodar páginas" else "Rotate pages", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text(if (pt) "Todas as páginas serão rodadas 90°. O original será mantido." else "All pages will be rotated 90°. The original file will be kept.", color = SecondaryText) },
        confirmButton = {
            Button(onClick = {
                val uri = rotateUri
                showRotate = false
                if (uri != null) runOperation(if (pt) "A rodar páginas…" else "Rotating pages…") { PdfOperations.rotateAllPages(context, uri) }
            }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (pt) "Rodar e guardar" else "Rotate and save") }
        },
        dismissButton = { TextButton(onClick = { showRotate = false }) { Text(if (pt) "Cancelar" else "Cancel") } }
    )

    if (showEditDialog && editUri != null) {
        PdfEditDialog(
            pt = pt,
            fileName = PdfOperations.displayName(context, editUri!!),
            onDismiss = { showEditDialog = false },
            onApplyText = { pageNumber, text ->
                val uri = editUri
                showEditDialog = false
                if (uri != null) runOperation(if (pt) "A adicionar texto…" else "Adding text…") {
                    PdfOperations.addTextAnnotation(context, uri, pageNumber, text)
                }
            },
            onApplySignature = { pageNumber, bitmap ->
                val uri = editUri
                showEditDialog = false
                if (uri != null) runOperation(if (pt) "A inserir assinatura…" else "Adding signature…") {
                    PdfOperations.addSignature(context, uri, pageNumber, bitmap)
                }
            }
        )
    }

    if (showCompressionDialog && compressionUri != null) {
        PdfCompressionDialog(
            pt = pt,
            fileName = PdfOperations.displayName(context, compressionUri!!),
            onDismiss = { showCompressionDialog = false },
            onCompress = { maxDimension, jpegQuality ->
                val uri = compressionUri
                showCompressionDialog = false
                if (uri != null) runOperation(if (pt) "A comprimir PDF…" else "Compressing PDF…") {
                    PdfOperations.compressRasterizedPdf(context, uri, maxDimension, jpegQuality)
                }
            }
        )
    }

    if (showPasswordDialog && passwordUri != null) {
        PdfPasswordDialog(
            pt = pt,
            fileName = passwordName,
            previousAttemptFailed = passwordWasWrong,
            onDismiss = { showPasswordDialog = false; passwordUri = null; passwordWasWrong = false },
            onSubmit = { enteredPassword ->
                val uri = passwordUri
                val name = passwordName
                showPasswordDialog = false
                passwordWasWrong = false
                if (uri != null) loadPdf(uri, 0, name, enteredPassword)
            }
        )
    }

    if (showProtectionDialog && protectUri != null) {
        PdfProtectionDialog(
            pt = pt,
            fileName = PdfOperations.displayName(context, protectUri!!),
            onDismiss = { showProtectionDialog = false; protectUri = null },
            onProtect = { newPassword ->
                val uri = protectUri
                showProtectionDialog = false
                if (uri != null) runOperation(if (pt) "A proteger PDF…" else "Protecting PDF…") {
                    PdfOperations.protectWithPassword(context, uri, newPassword)
                }
            }
        )
    }

    if (showAbout) AlertDialog(
        onDismissRequest = { showAbout = false }, containerColor = Panel,
        title = { Text("NexaPDF · 0.1.0", color = Color.White) },
        text = { Text(if (pt) "A primeira versão já lê PDFs, junta ficheiros, extrai páginas, roda páginas e cria PDFs a partir de imagens. As restantes ferramentas serão ativadas depois de implementadas e testadas." else "The first version reads PDFs, merges files, extracts pages, rotates pages and creates PDFs from images. More tools will be enabled after implementation and testing.", color = SecondaryText) },
        confirmButton = { TextButton(onClick = { showAbout = false }) { Text("OK") } }
    )

    if (busy) AlertDialog(
        onDismissRequest = {}, containerColor = Panel, confirmButton = {},
        title = { Text(busyText, color = Color.White) },
        text = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(color = Cyan, modifier = Modifier.size(26.dp)); Text(if (pt) "A processar no dispositivo…" else "Processing on this device…", color = SecondaryText) } }
    )
}

@Composable
private fun PdfPasswordDialog(
    pt: Boolean,
    fileName: String,
    previousAttemptFailed: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var password by remember(fileName) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (pt) "PDF protegido" else "Password-protected PDF", color = Color.White, fontWeight = FontWeight.Bold)
                Text(fileName, color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(
                    if (previousAttemptFailed) {
                        if (pt) "A palavra-passe está incorreta. Tenta novamente." else "That password was incorrect. Try again."
                    } else {
                        if (pt) "Este documento precisa de uma palavra-passe para ser aberto." else "This document needs a password to open."
                    },
                    color = if (previousAttemptFailed) Orange else SecondaryText,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (pt) "Palavra-passe do PDF" else "PDF password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = PanelAlt,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSubmit(password) }, enabled = password.isNotEmpty(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text(if (pt) "Abrir PDF" else "Open PDF")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (pt) "Cancelar" else "Cancel") } }
    )
}

@Composable
private fun PdfProtectionDialog(
    pt: Boolean,
    fileName: String,
    onDismiss: () -> Unit,
    onProtect: (String) -> Unit
) {
    var password by remember(fileName) { mutableStateOf("") }
    var confirmPassword by remember(fileName) { mutableStateOf("") }
    val valid = password.length >= 8 && password == confirmPassword
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (pt) "Proteger PDF" else "Protect PDF", color = Color.White, fontWeight = FontWeight.Bold)
                Text(fileName, color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (pt) "Cria uma cópia protegida por palavra-passe. O ficheiro original não será alterado." else "Create a password-protected copy. The original file will not be changed.",
                    color = SecondaryText, fontSize = 12.sp
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (pt) "Nova palavra-passe" else "New password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text(if (pt) "Usa pelo menos 8 caracteres." else "Use at least 8 characters.") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = PanelAlt, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (pt) "Confirmar palavra-passe" else "Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = confirmPassword.isNotEmpty() && password != confirmPassword,
                    supportingText = {
                        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
                            Text(if (pt) "As palavras-passe não coincidem." else "Passwords do not match.")
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Violet, unfocusedBorderColor = PanelAlt, focusedTextColor = Color.White, unfocusedTextColor = Color.White)
                )
                Text(
                    if (pt) "Se perderes a palavra-passe, poderás não conseguir recuperar o documento." else "If you lose this password, you may not be able to recover the document.",
                    color = Orange, fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = { onProtect(password) }, enabled = valid, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text(if (pt) "Proteger e guardar" else "Protect and save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(if (pt) "Cancelar" else "Cancel") } }
    )
}

@Composable
private fun PdfCompressionDialog(
    pt: Boolean,
    fileName: String,
    onDismiss: () -> Unit,
    onCompress: (Int, Int) -> Unit
) {
    var preset by remember { mutableStateOf("balanced") }
    val settings = when (preset) {
        "small" -> 900 to 52
        "sharp" -> 1600 to 84
        else -> 1200 to 68
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (pt) "Comprimir PDF" else "Compress PDF", color = Color.White, fontWeight = FontWeight.Bold)
                Text(fileName, color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (pt) "Escolhe a qualidade de saída." else "Choose the output quality.", color = SecondaryText, fontSize = 13.sp)
                listOf(
                    Triple("small", if (pt) "Mais pequeno" else "Smallest", if (pt) "900 px · menor qualidade" else "900 px · lower quality"),
                    Triple("balanced", if (pt) "Equilibrado" else "Balanced", if (pt) "1200 px · recomendado" else "1200 px · recommended"),
                    Triple("sharp", if (pt) "Mais nítido" else "Sharper", if (pt) "1600 px · maior qualidade" else "1600 px · higher quality")
                ).forEach { option ->
                    Surface(
                        color = if (preset == option.first) Violet.copy(alpha = 0.20f) else PanelAlt,
                        shape = RoundedCornerShape(12.dp),
                        border = if (preset == option.first) androidx.compose.foundation.BorderStroke(1.dp, Violet) else null,
                        modifier = Modifier.fillMaxWidth().clickable { preset = option.first }
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            RadioButton(selected = preset == option.first, onClick = { preset = option.first }, colors = RadioButtonDefaults.colors(selectedColor = Cyan))
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(option.second, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(option.third, color = SecondaryText, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Surface(color = Color(0xFF36251D), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.padding(11.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.WarningAmber, null, tint = Orange, modifier = Modifier.size(20.dp))
                        Text(
                            if (pt) "Este modo converte as páginas em imagens. O texto deixa de ser selecionável/pesquisável e formulários, links e conteúdo vetorial não são preservados. O original fica intacto. Só guardaremos o resultado se ficar mais pequeno." else "This mode converts pages into images. Text will no longer be selectable/searchable, and forms, links and vector content are not preserved. Your original stays intact. The result is saved only if it is smaller.",
                            color = Color(0xFFFFD9C8), fontSize = 11.sp, lineHeight = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onCompress(settings.first, settings.second) }, colors = ButtonDefaults.buttonColors(containerColor = Violet)) {
                Text(if (pt) "Comprimir e guardar" else "Compress and save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (pt) "Cancelar" else "Cancel") }
        }
    )
}

@Composable
private fun PdfEditDialog(
    pt: Boolean,
    fileName: String,
    onDismiss: () -> Unit,
    onApplyText: (Int, String) -> Unit,
    onApplySignature: (Int, Bitmap) -> Unit
) {
    var mode by remember { mutableStateOf("text") }
    var noteText by remember { mutableStateOf("") }
    var pageText by remember { mutableStateOf("1") }
    val strokes = remember { mutableStateListOf<MutableList<Offset>>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val hasSignature = strokes.any { it.isNotEmpty() }
    val pageNumber = pageText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (pt) "Editar PDF" else "Edit PDF", color = Color.White, fontWeight = FontWeight.Bold)
                Text(fileName, color = SecondaryText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == "text",
                        onClick = { mode = "text" },
                        label = { Text(if (pt) "Texto" else "Text") },
                        leadingIcon = { Icon(Icons.Filled.TextFields, null) }
                    )
                    FilterChip(
                        selected = mode == "signature",
                        onClick = { mode = "signature" },
                        label = { Text(if (pt) "Assinatura" else "Signature") },
                        leadingIcon = { Icon(Icons.Filled.Draw, null) }
                    )
                }
                OutlinedTextField(
                    value = pageText,
                    onValueChange = { pageText = it.filter(Char::isDigit).take(5) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(if (pt) "Número da página" else "Page number") },
                    supportingText = { Text(if (pt) "A página escolhida recebe o conteúdo na margem inferior." else "The selected page receives content near its bottom margin.") }
                )
                if (mode == "text") {
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                        label = { Text(if (pt) "Texto a adicionar" else "Text to add") },
                        placeholder = { Text(if (pt) "Escreve uma nota curta…" else "Write a short note…") },
                        supportingText = { Text(if (pt) "Até 180 caracteres; o ficheiro original é mantido." else "Up to 180 characters; the original stays unchanged.") }
                    )
                } else {
                    Text(
                        if (pt) "Desenha a assinatura com o dedo na área branca." else "Draw your signature with a finger in the white area.",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .pointerInput(mode) {
                                detectDragGestures(
                                    onDragStart = { point -> strokes.add(mutableStateListOf(point)) },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        strokes.lastOrNull()?.add(change.position)
                                    }
                                )
                            }
                    ) {
                        canvasSize = IntSize(size.width.toInt().coerceAtLeast(1), size.height.toInt().coerceAtLeast(1))
                        strokes.forEach { points ->
                            if (points.size == 1) {
                                drawCircle(Color(0xFF14263D), radius = 2.5f, center = points.first())
                            } else if (points.size > 1) {
                                val path = Path().apply {
                                    moveTo(points.first().x, points.first().y)
                                    points.drop(1).forEach { lineTo(it.x, it.y) }
                                }
                                drawPath(path, color = Color(0xFF14263D), style = Stroke(width = 4f))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { strokes.clear() }) {
                            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (pt) "Limpar assinatura" else "Clear signature")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = pageNumber != null && pageNumber > 0 && if (mode == "text") noteText.isNotBlank() else hasSignature && canvasSize.width > 0 && canvasSize.height > 0,
                onClick = {
                    val page = pageNumber ?: return@Button
                    if (mode == "text") onApplyText(page, noteText)
                    else onApplySignature(page, createSignatureBitmap(strokes, canvasSize))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) { Text(if (pt) "Aplicar e guardar" else "Apply and save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (pt) "Cancelar" else "Cancel") }
        }
    )
}

private fun createSignatureBitmap(strokes: List<List<Offset>>, size: IntSize): Bitmap {
    val width = 900
    val height = 280
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.rgb(20, 38, 61)
        style = AndroidPaint.Style.STROKE
        strokeWidth = 8f
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
    }
    val scaleX = width.toFloat() / size.width.coerceAtLeast(1)
    val scaleY = height.toFloat() / size.height.coerceAtLeast(1)
    strokes.forEach { points ->
        if (points.isNotEmpty()) {
            val path = AndroidPath()
            path.moveTo(points.first().x * scaleX, points.first().y * scaleY)
            points.drop(1).forEach { path.lineTo(it.x * scaleX, it.y * scaleY) }
            canvas.drawPath(path, paint)
        }
    }
    return bitmap
}

@Composable
private fun HomeScreen(
    pt: Boolean, recents: List<Recent>, query: String, onQuery: (String) -> Unit,
    onOpen: () -> Unit, onTool: (String) -> Unit, onRecent: (Recent) -> Unit, onAll: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BrandIcon()
                    Column {
                        Text("NexaPDF", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                        Text(if (pt) "O teu espaço de trabalho PDF" else "Your PDF workspace", color = SecondaryText, fontSize = 12.sp)
                    }
                }
                Surface(shape = CircleShape, color = PanelAlt, modifier = Modifier.size(42.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.Shield, null, tint = Cyan, modifier = Modifier.size(22.dp)) }
                }
            }
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = onQuery,
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = SecondaryText) },
                placeholder = { Text(if (pt) "Pesquisar documentos…" else "Search documents…", color = SecondaryText, fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp), colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet, unfocusedBorderColor = PanelAlt,
                    focusedTextColor = Color.White, unfocusedTextColor = Color.White
                )
            )
        }
        item {
            Surface(color = Violet, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
                Row(Modifier.background(Brush.horizontalGradient(listOf(Color(0xFF6546F4), Color(0xFF8D5CFA)))).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(color = Color.White.copy(alpha = 0.15f), shape = RoundedCornerShape(13.dp), modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.FolderOpen, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(if (pt) "Abrir PDF" else "Open PDF", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(if (pt) "Escolhe um ficheiro do teu dispositivo" else "Choose a file from your device", color = Color.White.copy(alpha = .82f), fontSize = 12.sp)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Color.White)
                }
            }
        }
        item { SectionHeading(if (pt) "Ferramentas principais" else "Main tools", if (pt) "Ver todas" else "See all", onAll) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.take(4).chunked(2).forEach { rowTools ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTools.forEach { tool -> ToolCard(tool, pt, Modifier.weight(1f)) { onTool(tool.id) } }
                    }
                }
            }
        }
        item { SectionHeading(if (pt) "Documentos recentes" else "Recent documents", if (pt) "Ver todos" else "View all", onAll) }
        val found = recents.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }.take(5)
        if (found.isEmpty()) {
            item {
                Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(Icons.Filled.Description, null, tint = SecondaryText, modifier = Modifier.size(30.dp))
                        Text(if (pt) "Os teus documentos aparecerão aqui" else "Your documents will appear here", color = Color.White, fontWeight = FontWeight.SemiBold)
                        Text(if (pt) "Abre um PDF para começar." else "Open a PDF to get started.", color = SecondaryText, fontSize = 12.sp)
                    }
                }
            }
        } else items(found, key = { it.uri }) { RecentRow(it, pt) { onRecent(it) } }
        item {
            Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Filled.Shield, null, tint = Cyan, modifier = Modifier.size(24.dp))
                    Column {
                        Text(if (pt) "Privacidade por defeito" else "Privacy by default", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(if (pt) "As ferramentas locais processam os ficheiros no dispositivo." else "Local tools process files on your device.", color = SecondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolsScreen(pt: Boolean, onTool: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        item { ScreenHeading(if (pt) "Ferramentas" else "Tools", if (pt) "Todas as ferramentas PDF, organizadas por tarefa." else "PDF tools, organized by task.") }
        items(tools, key = { it.id }) { tool ->
            Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth().clickable { onTool(tool.id) }) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                    ToolIcon(tool, 47)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(if (pt) tool.pt else tool.en, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(if (pt) tool.detailPt else tool.detailEn, color = SecondaryText, fontSize = 12.sp)
                        if (!tool.ready) Text(if (pt) "EM DESENVOLVIMENTO" else "IN DEVELOPMENT", color = Orange, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun DocumentsScreen(pt: Boolean, recents: List<Recent>, onOpen: () -> Unit, onRecent: (Recent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        ScreenHeading(if (pt) "Documentos" else "Documents", if (pt) "Os PDFs que abriste neste dispositivo." else "PDFs opened on this device.")
        Spacer(Modifier.height(14.dp))
        if (recents.isEmpty()) {
            Column(Modifier.fillMaxWidth().padding(top = 45.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.Description, null, tint = SecondaryText, modifier = Modifier.size(45.dp))
                Text(if (pt) "Ainda não há documentos" else "No documents yet", color = Color.White)
                Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (pt) "Abrir PDF" else "Open PDF") }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(recents, key = { it.uri }) { RecentRow(it, pt) { onRecent(it) } }
            item { Button(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (pt) "Abrir outro PDF" else "Open another PDF") } }
        }
    }
}

@Composable
private fun SettingsScreen(pt: Boolean, onLanguage: (String) -> Unit, onAbout: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ScreenHeading(if (pt) "Definições" else "Settings", if (pt) "Personaliza a tua experiência." else "Personalize your experience.")
        Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (pt) "Idioma da aplicação" else "App language", color = Color.White, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = { onLanguage("pt") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (pt) Violet else PanelAlt)) { Text("Português") }
                    Button(onClick = { onLanguage("en") }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = if (!pt) Violet else PanelAlt)) { Text("English") }
                }
            }
        }
        SettingRow(Icons.Filled.Shield, if (pt) "Privacidade" else "Privacy", if (pt) "Operações locais sem envio de PDFs para servidores." else "Local operations do not upload PDFs.") { onAbout() }
        SettingRow(Icons.Filled.Info, if (pt) "Sobre o NexaPDF" else "About NexaPDF", "0.1.0 · Android") { onAbout() }
    }
}

@Composable
private fun ReaderScreen(
    pt: Boolean, name: String, bitmap: Bitmap?, page: Int, count: Int,
    onBack: () -> Unit, onPrev: () -> Unit, onNext: () -> Unit, onShare: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(AppBg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((page + 1).toString() + " / " + count, color = SecondaryText, fontSize = 12.sp)
            }
            IconButton(onClick = onShare) { Icon(Icons.Filled.Share, null, tint = Cyan) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFE9EDF4)), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), contentDescription = "PDF page", modifier = Modifier.fillMaxSize().padding(5.dp))
            else CircularProgressIndicator(color = Violet)
        }
        Surface(color = Panel, shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (pt) "Leitor PDF" else "PDF reader", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (pt) "Pré-visualização local" else "Local preview", color = SecondaryText, fontSize = 11.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onPrev, enabled = page > 0, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = PanelAlt)) { Icon(Icons.Filled.ArrowBack, null); Spacer(Modifier.width(5.dp)); Text(if (pt) "Anterior" else "Previous") }
                    Button(onClick = onNext, enabled = page < count - 1, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Violet)) { Text(if (pt) "Seguinte" else "Next"); Spacer(Modifier.width(5.dp)); Icon(Icons.Filled.ChevronRight, null) }
                }
            }
        }
    }
}

@Composable
private fun ToolCard(tool: PdfTool, pt: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(17.dp), modifier = modifier.clickable { onClick() }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                ToolIcon(tool, 36)
                if (!tool.ready) Text(if (pt) "BREVE" else "SOON", color = SecondaryText, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (pt) tool.pt else tool.en, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (pt) tool.detailPt else tool.detailEn, color = SecondaryText, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ToolIcon(tool: PdfTool, size: Int) {
    Surface(color = tool.color.copy(alpha = .16f), shape = RoundedCornerShape(12.dp), modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size((size * .53f).dp)) }
    }
}

@Composable
private fun BrandIcon() {
    Surface(color = Violet, shape = RoundedCornerShape(13.dp), modifier = Modifier.size(44.dp)) {
        Box(Modifier.background(Brush.linearGradient(listOf(Violet, Blue))), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(27.dp))
        }
    }
}

@Composable
private fun RecentRow(doc: Recent, pt: Boolean, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Surface(color = Color(0xFF2D1B36), shape = RoundedCornerShape(9.dp), modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Filled.PictureAsPdf, null, tint = Pink, modifier = Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(doc.name, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (pt) "PDF · Neste dispositivo" else "PDF · On this device", color = SecondaryText, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = SecondaryText)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 4.dp)) { Text(action, color = Cyan, fontSize = 11.sp) }
    }
}

@Composable
private fun ScreenHeading(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = SecondaryText, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(color = Panel, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, tint = Cyan, modifier = Modifier.size(23.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(subtitle, color = SecondaryText, fontSize = 11.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = SecondaryText)
        }
    }
}
