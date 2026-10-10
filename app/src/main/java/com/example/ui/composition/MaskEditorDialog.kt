package com.example.ui.composition

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.CompositionLayer
import com.example.domain.model.MaskEditorTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

private enum class BrushMode {
    PRIMARY,   // Hide area (visibility mode) OR Paint blur (blur mode)
    SECONDARY  // Reveal area (visibility mode) OR Erase blur (blur mode)
}

@Composable
fun MaskEditorDialog(
    layer: CompositionLayer,
    target: MaskEditorTarget = MaskEditorTarget.VISIBILITY,
    onDismiss: () -> Unit,
    onApplyMask: (maskUri: Uri, isInverted: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isBlurMode = target == MaskEditorTarget.BLUR

    var brushMode by remember { mutableStateOf(BrushMode.PRIMARY) }
    var brushSize by remember { mutableFloatStateOf(32f) }
    var isSaving by remember { mutableStateOf(false) }

    // Mask Color customization
    val defaultBaseColor = if (isBlurMode) Color(0xFF1E88E5) else Color(0xFFE53935)
    var selectedOverlayColor by remember { mutableStateOf(defaultBaseColor) }
    var overlayAlpha by remember { mutableFloatStateOf(0.60f) }
    var showColorPalette by remember { mutableStateOf(false) }

    // Mask resolution for responsive editing
    val maskResolution = 512
    var maskBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var revision by remember { mutableIntStateOf(0) }

    // Preset high-contrast overlay colors
    val presetColors = remember {
        listOf(
            Color(0xFFE53935), // Rubylith Red
            Color(0xFF00E676), // Neon Green
            Color(0xFF2979FF), // Electric Blue
            Color(0xFF00E5FF), // Cyan
            Color(0xFFFFD600), // Amber / Yellow
            Color(0xFFE040FB), // Magenta
            Color(0xFFFFFFFF), // White
            Color(0xFF212121)  // Dark Slate
        )
    }

    // Initialize mask bitmap off-thread
    val sourceMaskUri = if (isBlurMode) layer.blurMaskUri else layer.maskUri
    LaunchedEffect(layer.id, target) {
        val bitmap = withContext(Dispatchers.IO) {
            val bmp = Bitmap.createBitmap(maskResolution, maskResolution, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)

            if (sourceMaskUri != null) {
                try {
                    context.contentResolver.openInputStream(sourceMaskUri)?.use { stream ->
                        val existing = BitmapFactory.decodeStream(stream)
                        if (existing != null) {
                            val src = Rect(0, 0, existing.width, existing.height)
                            val dst = Rect(0, 0, maskResolution, maskResolution)
                            canvas.drawBitmap(existing, src, dst, null)
                            existing.recycle()
                        } else {
                            bmp.eraseColor(if (isBlurMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
                        }
                    } ?: bmp.eraseColor(if (isBlurMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
                } catch (_: Exception) {
                    bmp.eraseColor(if (isBlurMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
                }
            } else {
                bmp.eraseColor(if (isBlurMode) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
            }
            bmp
        }
        maskBitmap = bitmap
    }

    Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxSize()
                .testTag("mask_editor_dialog"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onDismiss,
                            enabled = !isSaving,
                            modifier = Modifier.testTag("mask_editor_cancel_icon_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isBlurMode) "Paint Blur Region" else "Edit Layer Mask",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { showColorPalette = !showColorPalette },
                            modifier = Modifier.testTag("toggle_mask_color_palette_button")
                        ) {
                            Icon(
                                Icons.Default.Palette,
                                contentDescription = "Mask Overlay Color",
                                tint = selectedOverlayColor
                            )
                        }

                        Button(
                            onClick = {
                                val currentMask = maskBitmap ?: return@Button
                                isSaving = true
                                scope.launch {
                                    try {
                                        val maskFile = withContext(Dispatchers.IO) {
                                            val maskDir = File(context.filesDir, "masks").apply { if (!exists()) mkdirs() }
                                            val prefix = if (isBlurMode) "mask_blur" else "mask"
                                            val file = File(maskDir, "${prefix}_${layer.id}_${System.currentTimeMillis()}.png")
                                            FileOutputStream(file).use { out ->
                                                currentMask.compress(Bitmap.CompressFormat.PNG, 100, out)
                                                out.flush()
                                            }
                                            file
                                        }
                                        val uri = Uri.fromFile(maskFile)
                                        onApplyMask(uri, false)
                                    } catch (_: Exception) {
                                        onDismiss()
                                    } finally {
                                        isSaving = false
                                    }
                                }
                            },
                            enabled = !isSaving && maskBitmap != null,
                            modifier = Modifier.testTag("mask_editor_apply_button")
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Apply", modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Apply")
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Custom Color Palette Card (when expanded)
                if (showColorPalette) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("mask_color_picker_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Mask Overlay Color & Opacity",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = "Reset Default",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable {
                                            selectedOverlayColor = defaultBaseColor
                                            overlayAlpha = 0.60f
                                        }
                                        .testTag("reset_mask_color_button")
                                )
                            }

                            // Preset Color Swatches
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                presetColors.forEach { color ->
                                    val isSelected = color == selectedOverlayColor
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                                                shape = CircleShape
                                            )
                                            .clickable { selectedOverlayColor = color }
                                    )
                                }
                            }

                            // Opacity Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Opacity",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.width(50.dp)
                                )
                                Slider(
                                    value = overlayAlpha,
                                    onValueChange = { overlayAlpha = it },
                                    valueRange = 0.15f..0.90f,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("mask_overlay_opacity_slider")
                                )
                                Text(
                                    text = "${(overlayAlpha * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(36.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // Informational badge
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(selectedOverlayColor.copy(alpha = overlayAlpha))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isBlurMode) {
                                "Overlay marks the region that will be blurred/obscured."
                            } else {
                                "Overlay marks hidden areas. Paint over regions to hide or reveal them."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Center: Interactive Painting Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val aspectRatio = if (layer.intrinsicHeight > 0) {
                        (layer.intrinsicWidth.toFloat() / layer.intrinsicHeight.toFloat()).coerceIn(0.2f, 5f)
                    } else 1f

                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(aspectRatio)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF1E1E1E))
                    ) {
                        val canvasWidth = constraints.maxWidth.toFloat()
                        val canvasHeight = constraints.maxHeight.toFloat()

                        // 1. Underneath: Original Image
                        val imageRequest = remember(layer.uri) {
                            ImageRequest.Builder(context)
                                .data(layer.uri)
                                .size(maskResolution)
                                .crossfade(false)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = "Source Layer",
                            contentScale = ContentScale.FillBounds,
                            modifier = Modifier.fillMaxSize()
                        )

                        // 2. Interactive Mask Overlay Canvas
                        val currentMask = maskBitmap
                        if (currentMask != null) {
                            androidx.compose.foundation.Canvas(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("mask_paint_canvas")
                                    .pointerInput(brushMode, brushSize, currentMask, isBlurMode) {
                                        var lastPoint: Offset? = null
                                        val maskCanvas = Canvas(currentMask)
                                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                            style = Paint.Style.STROKE
                                            strokeCap = Paint.Cap.ROUND
                                            strokeJoin = Paint.Join.ROUND
                                        }

                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                lastPoint = offset
                                                val normX = (offset.x / canvasWidth) * maskResolution
                                                val normY = (offset.y / canvasHeight) * maskResolution
                                                val strokeW = (brushSize / canvasWidth) * maskResolution

                                                paint.strokeWidth = strokeW.coerceAtLeast(2f)
                                                if (isBlurMode) {
                                                    // Blur mode: PRIMARY paints blur (white), SECONDARY erases blur (clear)
                                                    if (brushMode == BrushMode.PRIMARY) {
                                                        paint.color = android.graphics.Color.WHITE
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                                                    } else {
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                                    }
                                                } else {
                                                    // Visibility mode: PRIMARY hides (clear alpha), SECONDARY reveals (white alpha)
                                                    if (brushMode == BrushMode.PRIMARY) {
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                                    } else {
                                                        paint.color = android.graphics.Color.WHITE
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                                                    }
                                                }
                                                maskCanvas.drawCircle(normX, normY, strokeW / 2f, paint)
                                                revision++
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                val start = lastPoint ?: change.position
                                                val end = change.position
                                                lastPoint = end

                                                val x1 = (start.x / canvasWidth) * maskResolution
                                                val y1 = (start.y / canvasHeight) * maskResolution
                                                val x2 = (end.x / canvasWidth) * maskResolution
                                                val y2 = (end.y / canvasHeight) * maskResolution
                                                val strokeW = (brushSize / canvasWidth) * maskResolution

                                                paint.strokeWidth = strokeW.coerceAtLeast(2f)
                                                if (isBlurMode) {
                                                    if (brushMode == BrushMode.PRIMARY) {
                                                        paint.color = android.graphics.Color.WHITE
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                                                    } else {
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                                    }
                                                } else {
                                                    if (brushMode == BrushMode.PRIMARY) {
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                                    } else {
                                                        paint.color = android.graphics.Color.WHITE
                                                        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC)
                                                    }
                                                }
                                                maskCanvas.drawLine(x1, y1, x2, y2, paint)
                                                revision++
                                            },
                                            onDragEnd = { lastPoint = null },
                                            onDragCancel = { lastPoint = null }
                                        )
                                    }
                            ) {
                                @Suppress("UNUSED_VARIABLE")
                                val rev = revision // read state to trigger redraw

                                val overlayBmp = Bitmap.createBitmap(maskResolution, maskResolution, Bitmap.Config.ARGB_8888)
                                val oCanvas = Canvas(overlayBmp)

                                val aInt = (overlayAlpha * 255).roundToInt().coerceIn(0, 255)
                                val rInt = (selectedOverlayColor.red * 255).roundToInt().coerceIn(0, 255)
                                val gInt = (selectedOverlayColor.green * 255).roundToInt().coerceIn(0, 255)
                                val bInt = (selectedOverlayColor.blue * 255).roundToInt().coerceIn(0, 255)
                                val overlayColorInt = android.graphics.Color.argb(aInt, rInt, gInt, bInt)

                                oCanvas.drawColor(overlayColorInt)

                                val oPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                    xfermode = if (isBlurMode) {
                                        // In blur mode, mask alpha == 255 indicates blur region.
                                        // We want overlay color to show WHERE mask has alpha!
                                        PorterDuffXfermode(PorterDuff.Mode.DST_IN)
                                    } else {
                                        // In visibility mode, mask alpha == 0 indicates hidden region.
                                        // We want overlay color to show WHERE mask has NO alpha!
                                        PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
                                    }
                                }
                                oCanvas.drawBitmap(currentMask, 0f, 0f, oPaint)

                                val composeImg = overlayBmp.asImageBitmap()
                                drawImage(
                                    image = composeImg,
                                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
                                )
                                overlayBmp.recycle()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Controls: Tool selectors, brush size, reset/invert actions
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Brush Mode
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val primaryLabel = if (isBlurMode) "Paint Blur Area" else "Erase / Hide Area"
                            val secondaryLabel = if (isBlurMode) "Erase Blur Area" else "Paint / Reveal Area"

                            FilterChip(
                                selected = brushMode == BrushMode.PRIMARY,
                                onClick = { brushMode = BrushMode.PRIMARY },
                                label = { Text(primaryLabel, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        if (isBlurMode) Icons.Default.Brush else Icons.Default.VisibilityOff,
                                        contentDescription = primaryLabel,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (isBlurMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                                    selectedLabelColor = if (isBlurMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_tool_hide_chip")
                            )

                            FilterChip(
                                selected = brushMode == BrushMode.SECONDARY,
                                onClick = { brushMode = BrushMode.SECONDARY },
                                label = { Text(secondaryLabel, fontSize = 12.sp) },
                                leadingIcon = {
                                    Icon(
                                        if (isBlurMode) Icons.Default.Clear else Icons.Default.Visibility,
                                        contentDescription = secondaryLabel,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_tool_reveal_chip")
                            )
                        }

                        // 2. Brush Size Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.Brush,
                                contentDescription = "Brush Size",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Slider(
                                value = brushSize,
                                onValueChange = { brushSize = it },
                                valueRange = 10f..80f,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_brush_size_slider")
                            )

                            Text(
                                text = "${brushSize.roundToInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(36.dp)
                            )
                        }

                        // 3. Action Buttons: Invert, Clear All, Reset All
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    maskBitmap?.let { bmp ->
                                        val pixels = IntArray(bmp.width * bmp.height)
                                        bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                                        for (i in pixels.indices) {
                                            val a = (pixels[i] ushr 24) and 0xFF
                                            val invA = 255 - a
                                            pixels[i] = (invA shl 24) or (0x00FFFFFF and pixels[i])
                                        }
                                        bmp.setPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                                        revision++
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_invert_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Flip, contentDescription = "Invert", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Invert", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    maskBitmap?.eraseColor(android.graphics.Color.TRANSPARENT)
                                    revision++
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_clear_all_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBlurMode) "Clear Blur" else "Hide All", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    maskBitmap?.eraseColor(android.graphics.Color.WHITE)
                                    revision++
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("mask_reset_all_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isBlurMode) "Blur All" else "Show All", fontSize = 11.sp, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}
