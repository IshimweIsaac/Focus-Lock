package com.example

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ReaderTheme(val title: String) {
    LIGHT("Light"),
    SEPIA("Sepia"),
    DARK("Dark")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfLibraryScreen(
    onOpenDoc: (FocusPdfDoc) -> Unit,
    onBack: () -> Unit,
    onPickPdfRequested: () -> Unit,
    onPickPdfFinished: () -> Unit
) {
    val context = LocalContext.current
    var docs by remember { mutableStateOf(emptyList<FocusPdfDoc>()) }
    var isImporting by remember { mutableStateOf(false) }
    var docToDelete by remember { mutableStateOf<FocusPdfDoc?>(null) }
    val scope = rememberCoroutineScope()

    fun refreshDocs() {
        docs = PdfDocumentManager.getDocs(context)
    }

    LaunchedEffect(Unit) {
        PdfDocumentManager.ensureSampleDocExists(context)
        refreshDocs()
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        onPickPdfFinished()
        if (uri != null) {
            isImporting = true
            scope.launch {
                val newDoc = withContext(Dispatchers.IO) {
                    PdfDocumentManager.importPdfFromUri(context, uri)
                }
                isImporting = false
                if (newDoc != null) {
                    refreshDocs()
                    Toast.makeText(context, "Imported: ${newDoc.title}", Toast.LENGTH_SHORT).show()
                    onOpenDoc(newDoc)
                } else {
                    Toast.makeText(context, "Could not import PDF. Please verify file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun triggerFilePicker() {
        onPickPdfRequested()
        try {
            openDocumentLauncher.launch(arrayOf("application/pdf"))
        } catch (e: Exception) {
            onPickPdfFinished()
            Toast.makeText(context, "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }

    BackHandler {
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Reading Library", fontWeight = FontWeight.SemiBold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = { triggerFilePicker() },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Import PDF", fontSize = 13.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { triggerFilePicker() },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Import PDF") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (docs.isEmpty() && !isImporting) {
                // Empty state
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No Documents Yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Import PDF files to read books, study notes, or scriptures offline during lockdown.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = { triggerFilePicker() },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import PDF from Phone")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "These documents are accessible even in strict lockdown. No other phone apps can be opened.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    items(docs, key = { it.id }) { doc ->
                        DocListItemCard(
                            doc = doc,
                            onClick = { onOpenDoc(doc) },
                            onDelete = { docToDelete = doc }
                        )
                    }
                }
            }

            if (isImporting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Importing PDF...", fontWeight = FontWeight.Medium)
                            Text("Copying to local secure storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (docToDelete != null) {
        val target = docToDelete!!
        AlertDialog(
            onDismissRequest = { docToDelete = null },
            title = { Text("Delete Document?") },
            text = { Text("Are you sure you want to delete \"${target.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        PdfDocumentManager.deleteDoc(context, target.id)
                        docToDelete = null
                        refreshDocs()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { docToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DocListItemCard(
    doc: FocusPdfDoc,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val percent = if (doc.pageCount > 0) {
        ((doc.lastReadPage + 1).toFloat() / doc.pageCount * 100).toInt().coerceIn(0, 100)
    } else 0

    val formattedSize = remember(doc.fileSizeBytes) {
        when {
            doc.fileSizeBytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", doc.fileSizeBytes / (1024f * 1024f))
            doc.fileSizeBytes >= 1024 -> String.format(Locale.US, "%d KB", doc.fileSizeBytes / 1024)
            else -> "${doc.fileSizeBytes} B"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${doc.pageCount} ${if (doc.pageCount == 1) "page" else "pages"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                // Progress bar
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (doc.lastReadPage == 0) "Not started" else "Page ${doc.lastReadPage + 1} of ${doc.pageCount} ($percent%)",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    doc: FocusPdfDoc,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    var currentPage by remember { mutableIntStateOf(doc.lastReadPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPage by remember { mutableStateOf(true) }
    var renderEngine by remember { mutableStateOf<PdfRenderEngine?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // UI state
    var showControls by remember { mutableStateOf(true) }
    var currentTheme by remember { mutableStateOf(ReaderTheme.LIGHT) }
    var showJumpDialog by remember { mutableStateOf(false) }

    // Zoom & Pan state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Initialize renderer safely
    DisposableEffect(doc.id) {
        val file = PdfDocumentManager.getFileForDoc(context, doc)
        val engine = PdfRenderEngine(file)
        renderEngine = engine
        if (!engine.isValid) {
            errorMessage = "Unable to read this document. It may be password-protected or unsupported."
        }

        onDispose {
            engine.close()
            // Save last read page when leaving
            PdfDocumentManager.updateLastReadPage(context, doc.id, currentPage)
        }
    }

    // Load page bitmap whenever currentPage changes
    LaunchedEffect(currentPage, renderEngine) {
        val engine = renderEngine ?: return@LaunchedEffect
        if (!engine.isValid) return@LaunchedEffect

        isLoadingPage = true
        // Target high-definition reading width
        val screenWidthPx = with(density) { 1080.dp.roundToPx() }
        val bmp = engine.getPageBitmap(currentPage, screenWidthPx)
        currentBitmap = bmp
        isLoadingPage = false

        // Reset zoom on page flip
        scale = 1f
        offset = Offset.Zero

        // Auto save progress
        PdfDocumentManager.updateLastReadPage(context, doc.id, currentPage)
    }

    BackHandler {
        onBack()
    }

    // Inverted color matrix for dark reading mode
    val invertMatrix = remember {
        ColorMatrix(
            floatArrayOf(
                -1.0f,  0.0f,  0.0f, 0.0f, 255f,
                 0.0f, -1.0f,  0.0f, 0.0f, 255f,
                 0.0f,  0.0f, -1.0f, 0.0f, 255f,
                 0.0f,  0.0f,  0.0f, 1.0f,   0f
            )
        )
    }

    val pageBackgroundColor = when (currentTheme) {
        ReaderTheme.LIGHT -> Color(0xFFF3F4F6)
        ReaderTheme.SEPIA -> Color(0xFFF4ECD8)
        ReaderTheme.DARK -> Color(0xFF121212)
    }

    Scaffold(
        containerColor = pageBackgroundColor,
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = doc.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Page ${currentPage + 1} of ${doc.pageCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Theme selector button
                        TextButton(
                            onClick = {
                                currentTheme = when (currentTheme) {
                                    ReaderTheme.LIGHT -> ReaderTheme.SEPIA
                                    ReaderTheme.SEPIA -> ReaderTheme.DARK
                                    ReaderTheme.DARK -> ReaderTheme.LIGHT
                                }
                            }
                        ) {
                            Text(
                                text = currentTheme.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Reset zoom if zoomed in
                        if (scale > 1.05f) {
                            IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Reset Zoom")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Slider for scrubbing pages
                        if (doc.pageCount > 1) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${currentPage + 1}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.Center
                                )
                                Slider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { currentPage = it.toInt() },
                                    valueRange = 0f..(doc.pageCount - 1).toFloat(),
                                    steps = if (doc.pageCount > 2) doc.pageCount - 2 else 0,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    "${doc.pageCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.width(32.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Previous Page
                            IconButton(
                                onClick = {
                                    if (currentPage > 0) currentPage--
                                },
                                enabled = currentPage > 0
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Page")
                            }

                            // Jump to page button
                            OutlinedButton(
                                onClick = { showJumpDialog = true },
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
                            ) {
                                Text("Page ${currentPage + 1} of ${doc.pageCount}", fontSize = 13.sp)
                            }

                            // Next Page
                            IconButton(
                                onClick = {
                                    if (currentPage < doc.pageCount - 1) currentPage++
                                },
                                enabled = currentPage < doc.pageCount - 1
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Page")
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showControls = !showControls
                        },
                        onDoubleTap = {
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2f
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale = (scale * zoom).coerceIn(1f, 4f)
                        scale = newScale

                        if (newScale > 1f) {
                            // Bound panning within viewport boundaries
                            val maxPanX = (newScale - 1f) * 600f
                            val maxPanY = (newScale - 1f) * 800f
                            offset = Offset(
                                x = (offset.x + pan.x).coerceIn(-maxPanX, maxPanX),
                                y = (offset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                            )
                        } else {
                            offset = Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (errorMessage != null) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        errorMessage ?: "Error",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onBack) {
                        Text("Return to Library")
                    }
                }
            } else if (currentBitmap != null) {
                val colorFilter = when (currentTheme) {
                    ReaderTheme.DARK -> ColorFilter.colorMatrix(invertMatrix)
                    ReaderTheme.SEPIA -> null
                    ReaderTheme.LIGHT -> null
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = if (currentTheme == ReaderTheme.DARK) 0.dp else 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentTheme == ReaderTheme.SEPIA) Color(0xFFF7F0E0) else Color.White
                        )
                    ) {
                        Image(
                            bitmap = currentBitmap!!.asImageBitmap(),
                            contentDescription = "PDF Page ${currentPage + 1}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                            colorFilter = colorFilter
                        )
                    }
                }
            }

            if (isLoadingPage) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.5.dp)
                }
            }
        }
    }

    // Jump to page dialog
    if (showJumpDialog) {
        var inputPageText by remember { mutableStateOf((currentPage + 1).toString()) }
        AlertDialog(
            onDismissRequest = { showJumpDialog = false },
            title = { Text("Go to Page") },
            text = {
                Column {
                    Text("Enter page number between 1 and ${doc.pageCount}:", fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputPageText,
                        onValueChange = { inputPageText = it.filter { char -> char.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = inputPageText.toIntOrNull()
                        if (target != null && target in 1..doc.pageCount) {
                            currentPage = target - 1
                        }
                        showJumpDialog = false
                    }
                ) {
                    Text("Go")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJumpDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
