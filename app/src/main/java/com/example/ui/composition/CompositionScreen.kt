package com.example.ui.composition

import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AlignHorizontalCenter
import androidx.compose.material.icons.filled.AlignHorizontalLeft
import androidx.compose.material.icons.filled.AlignHorizontalRight
import androidx.compose.material.icons.filled.AlignVerticalBottom
import androidx.compose.material.icons.filled.AlignVerticalCenter
import androidx.compose.material.icons.filled.AlignVerticalTop
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.FlipToBack
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.domain.model.CanvasBackground
import com.example.domain.model.CompositionLayer
import com.example.domain.model.ExportFormat
import com.example.domain.model.NormalizedCropRect
import com.example.ui.composition.CompositionViewModel.AlignmentType
import com.example.utils.MaskTransformation
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun CompositionScreen(
    viewModel: CompositionViewModel,
    onSendToImg2img: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addLayers(uris)
        }
    }

    var selectedFormat by remember { mutableStateOf(ExportFormat.PNG) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Graphiton",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Local Layer Composition",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "1024×1024",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // 2. Canvas Area or Empty State
        if (state.layers.isEmpty()) {
            EmptyCompositionState(
                onAddImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            )
        } else {
            CompositionCanvas(
                state = state,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        RoundedCornerShape(14.dp)
                    )
                    .testTag("composition_canvas_container")
            )

            // 3. Layer Controls Bar (When layer selected)
            if (state.selectedLayer != null) {
                if (state.isCropping) {
                    CropControlsBar(
                        onCancel = { viewModel.cancelCrop() },
                        onReset = { viewModel.resetSelectedCrop() },
                        onApply = { viewModel.applyCrop() }
                    )
                } else {
                    LayerEditPanel(
                        selectedLayer = state.selectedLayer!!,
                        viewModel = viewModel,
                        onStartCrop = { viewModel.startCropping() },
                        onMoveForward = { viewModel.moveForward() },
                        onMoveBackward = { viewModel.moveBackward() },
                        onBringToFront = { viewModel.bringToFront() },
                        onSendToBack = { viewModel.sendToBack() },
                        onDelete = { viewModel.deleteSelectedLayer() }
                    )
                }
            }

            // 4. Background & Format Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Background Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Background:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = state.background == CanvasBackground.TRANSPARENT,
                        onClick = { viewModel.setBackground(CanvasBackground.TRANSPARENT) },
                        label = { Text("Transparent", fontSize = 12.sp) },
                        modifier = Modifier.testTag("bg_transparent_chip")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = state.background == CanvasBackground.WHITE,
                        onClick = { viewModel.setBackground(CanvasBackground.WHITE) },
                        label = { Text("White", fontSize = 12.sp) },
                        modifier = Modifier.testTag("bg_white_chip")
                    )
                }

                // Format Switch (PNG / JPEG)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Format:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    FilterChip(
                        selected = selectedFormat == ExportFormat.PNG,
                        onClick = { selectedFormat = ExportFormat.PNG },
                        label = { Text("PNG", fontSize = 12.sp) },
                        modifier = Modifier.testTag("format_png_chip")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = selectedFormat == ExportFormat.JPEG,
                        onClick = { selectedFormat = ExportFormat.JPEG },
                        label = { Text("JPEG", fontSize = 12.sp) },
                        modifier = Modifier.testTag("format_jpeg_chip")
                    )
                }
            }

            // 5. Horizontal Thumbnail Strip
            CompositionThumbnailStrip(
                layers = state.layers,
                selectedLayerId = state.selectedLayerId,
                onSelectLayer = { viewModel.selectLayer(it) },
                onDeleteLayer = { viewModel.deleteLayer(it) },
                onAddImage = {
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("composition_thumbnail_strip")
            )

            // 6. Action Buttons: Add Image, Save, Share
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("composition_add_image_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add image", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Image", maxLines = 1)
                }

                Button(
                    onClick = { viewModel.exportComposition(selectedFormat) },
                    enabled = !state.isExporting,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("composition_save_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = "Save composition", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save", maxLines = 1)
                    }
                }

                Button(
                    onClick = {
                        if (state.lastExportedFilePath != null) {
                            shareCompositionFile(context, File(state.lastExportedFilePath!!), selectedFormat)
                        } else {
                            // Export first then share
                            viewModel.exportComposition(selectedFormat) { file ->
                                shareCompositionFile(context, file, selectedFormat)
                            }
                        }
                    },
                    enabled = !state.isExporting,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("composition_share_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share composition", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", maxLines = 1)
                }
            }

            // 7. Send Final Composition to img2img Workflow
            Button(
                onClick = {
                    viewModel.prepareCompositionForImg2img { uri ->
                        onSendToImg2img(uri)
                    }
                },
                enabled = state.layers.isNotEmpty() && !state.isExporting && !state.isPreparingImg2img,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("composition_send_to_img2img_button"),
                shape = RoundedCornerShape(10.dp)
            ) {
                if (state.isPreparingImg2img) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Rendering composition for img2img...", fontSize = 14.sp)
                } else {
                    Icon(Icons.Default.AutoAwesome, contentDescription = "Send to img2img", modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Use in img2img Generation", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // Mask Editor Modal Dialog
        if (state.isMasking && state.selectedLayer != null) {
            MaskEditorDialog(
                layer = state.selectedLayer!!,
                target = state.maskEditorTarget,
                onDismiss = { viewModel.closeMaskEditor() },
                onApplyMask = { uri, isInverted ->
                    viewModel.applyMaskToSelectedLayer(uri, isInverted)
                }
            )
        }
    }
}

@Composable
private fun EmptyCompositionState(
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .testTag("empty_composition_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Composition Layers",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Create a Composition",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Add multiple images as layers.\nDrag, pinch to zoom, and rotate to arrange.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onAddImage,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("empty_state_add_image_button")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = "Add image")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Images")
            }
        }
    }
}

private enum class CropHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    TOP_EDGE,
    BOTTOM_EDGE,
    LEFT_EDGE,
    RIGHT_EDGE,
    INSIDE_MOVE,
    NONE
}

private enum class EditTool(val label: String) {
    OPACITY("Opacity"),
    ROTATE("Rotate"),
    ALIGN("Align"),
    CROP("Crop"),
    MASK("Mask"),
    BLUR("Blur"),
    ORDER("Order")
}

@Composable
private fun CompositionCanvas(
    state: com.example.domain.model.CompositionState,
    viewModel: CompositionViewModel,
    modifier: Modifier = Modifier
) {
    val sortedLayers = remember(state.layers) {
        state.layers.filter { it.isVisible }.sortedBy { it.zIndex }
    }
    val currentLayers by rememberUpdatedState(sortedLayers)
    val currentSelectedId by rememberUpdatedState(state.selectedLayerId)
    val dashPathEffect = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }

    BoxWithConstraints(
        modifier = modifier
            .background(
                if (state.background == CanvasBackground.WHITE) Color.White else Color.Transparent
            )
            .drawBehind {
                if (state.background == CanvasBackground.TRANSPARENT) {
                    val squareSize = 20.dp.toPx()
                    val cols = (size.width / squareSize).toInt() + 1
                    val rows = (size.height / squareSize).toInt() + 1
                    for (c in 0 until cols) {
                        for (r in 0 until rows) {
                            val color = if ((c + r) % 2 == 0) Color(0xFF262626) else Color(0xFF1E1E1E)
                            drawRect(
                                color = color,
                                topLeft = Offset(c * squareSize, r * squareSize),
                                size = Size(squareSize, squareSize)
                            )
                        }
                    }
                }
            }
            .pointerInput(state.isCropping, state.selectedLayerId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val canvasWidthPx = size.width.toFloat()
                    val canvasScale = canvasWidthPx / state.canvasWidth.toFloat()

                    if (state.isCropping && state.selectedLayer != null && state.cropDraft != null) {
                        // In Crop Mode: handle crop boundary dragging
                        val selected = state.selectedLayer!!
                        val transform = viewModel.getTransform(selected.id)
                        val touchX = down.position.x / canvasScale
                        val touchY = down.position.y / canvasScale

                        val dx = touchX - transform.x
                        val dy = touchY - transform.y
                        val rad = Math.toRadians(-transform.rotation.toDouble())
                        val cosVal = cos(rad)
                        val sinVal = sin(rad)
                        val unrotX = (dx * cosVal - dy * sinVal).toFloat()
                        val unrotY = (dx * sinVal + dy * cosVal).toFloat()

                        val layerScale = transform.scale
                        val normX = (unrotX / (selected.intrinsicWidth * layerScale)) + 0.5f
                        val normY = (unrotY / (selected.intrinsicHeight * layerScale)) + 0.5f

                        val tolPx = 36.dp.toPx()
                        val tolX = (tolPx / canvasScale) / (selected.intrinsicWidth * layerScale)
                        val tolY = (tolPx / canvasScale) / (selected.intrinsicHeight * layerScale)

                        val draft = state.cropDraft!!
                        val hitHandle = when {
                            abs(normX - draft.left) <= tolX && abs(normY - draft.top) <= tolY -> CropHandle.TOP_LEFT
                            abs(normX - draft.right) <= tolX && abs(normY - draft.top) <= tolY -> CropHandle.TOP_RIGHT
                            abs(normX - draft.left) <= tolX && abs(normY - draft.bottom) <= tolY -> CropHandle.BOTTOM_LEFT
                            abs(normX - draft.right) <= tolX && abs(normY - draft.bottom) <= tolY -> CropHandle.BOTTOM_RIGHT
                            abs(normY - draft.top) <= tolY && normX in (draft.left - tolX)..(draft.right + tolX) -> CropHandle.TOP_EDGE
                            abs(normY - draft.bottom) <= tolY && normX in (draft.left - tolX)..(draft.right + tolX) -> CropHandle.BOTTOM_EDGE
                            abs(normX - draft.left) <= tolX && normY in (draft.top - tolY)..(draft.bottom + tolY) -> CropHandle.LEFT_EDGE
                            abs(normX - draft.right) <= tolX && normY in (draft.top - tolY)..(draft.bottom + tolY) -> CropHandle.RIGHT_EDGE
                            normX in draft.left..draft.right && normY in draft.top..draft.bottom -> CropHandle.INSIDE_MOVE
                            else -> CropHandle.NONE
                        }

                        if (hitHandle != CropHandle.NONE) {
                            down.consume()
                            var currentDraft = draft
                            do {
                                val event = awaitPointerEvent()
                                val canceled = event.changes.any { it.isConsumed }
                                if (!canceled) {
                                    val pan = event.calculatePan()
                                    val unrotDeltaX = ((pan.x / canvasScale) * cosVal - (pan.y / canvasScale) * sinVal).toFloat()
                                    val unrotDeltaY = ((pan.x / canvasScale) * sinVal + (pan.y / canvasScale) * cosVal).toFloat()
                                    val deltaNormX = unrotDeltaX / (selected.intrinsicWidth * layerScale)
                                    val deltaNormY = unrotDeltaY / (selected.intrinsicHeight * layerScale)

                                    when (hitHandle) {
                                        CropHandle.TOP_LEFT -> {
                                            val newL = (currentDraft.left + deltaNormX).coerceIn(0f, currentDraft.right - 0.05f)
                                            val newT = (currentDraft.top + deltaNormY).coerceIn(0f, currentDraft.bottom - 0.05f)
                                            currentDraft = currentDraft.copy(left = newL, top = newT)
                                        }
                                        CropHandle.TOP_RIGHT -> {
                                            val newR = (currentDraft.right + deltaNormX).coerceIn(currentDraft.left + 0.05f, 1f)
                                            val newT = (currentDraft.top + deltaNormY).coerceIn(0f, currentDraft.bottom - 0.05f)
                                            currentDraft = currentDraft.copy(right = newR, top = newT)
                                        }
                                        CropHandle.BOTTOM_LEFT -> {
                                            val newL = (currentDraft.left + deltaNormX).coerceIn(0f, currentDraft.right - 0.05f)
                                            val newB = (currentDraft.bottom + deltaNormY).coerceIn(currentDraft.top + 0.05f, 1f)
                                            currentDraft = currentDraft.copy(left = newL, bottom = newB)
                                        }
                                        CropHandle.BOTTOM_RIGHT -> {
                                            val newR = (currentDraft.right + deltaNormX).coerceIn(currentDraft.left + 0.05f, 1f)
                                            val newB = (currentDraft.bottom + deltaNormY).coerceIn(currentDraft.top + 0.05f, 1f)
                                            currentDraft = currentDraft.copy(right = newR, bottom = newB)
                                        }
                                        CropHandle.TOP_EDGE -> {
                                            val newT = (currentDraft.top + deltaNormY).coerceIn(0f, currentDraft.bottom - 0.05f)
                                            currentDraft = currentDraft.copy(top = newT)
                                        }
                                        CropHandle.BOTTOM_EDGE -> {
                                            val newB = (currentDraft.bottom + deltaNormY).coerceIn(currentDraft.top + 0.05f, 1f)
                                            currentDraft = currentDraft.copy(bottom = newB)
                                        }
                                        CropHandle.LEFT_EDGE -> {
                                            val newL = (currentDraft.left + deltaNormX).coerceIn(0f, currentDraft.right - 0.05f)
                                            currentDraft = currentDraft.copy(left = newL)
                                        }
                                        CropHandle.RIGHT_EDGE -> {
                                            val newR = (currentDraft.right + deltaNormX).coerceIn(currentDraft.left + 0.05f, 1f)
                                            currentDraft = currentDraft.copy(right = newR)
                                        }
                                        CropHandle.INSIDE_MOVE -> {
                                            val w = currentDraft.right - currentDraft.left
                                            val h = currentDraft.bottom - currentDraft.top
                                            val newL = (currentDraft.left + deltaNormX).coerceIn(0f, 1f - w)
                                            val newT = (currentDraft.top + deltaNormY).coerceIn(0f, 1f - h)
                                            currentDraft = currentDraft.copy(left = newL, top = newT, right = newL + w, bottom = newT + h)
                                        }
                                        CropHandle.NONE -> {}
                                    }
                                    viewModel.updateCropDraft(currentDraft)
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            } while (!canceled && event.changes.any { it.pressed })
                        }
                    } else {
                        // Normal mode: select and transform layers (pan, pinch zoom, rotate)
                        val tapLogicalX = down.position.x / canvasScale
                        val tapLogicalY = down.position.y / canvasScale

                        val layers = currentLayers
                        val hitLayer = layers
                            .sortedByDescending { it.zIndex }
                            .firstOrNull { layer ->
                                val transform = viewModel.getTransform(layer.id)
                                isPointInsideLayer(
                                    tapLogicalX,
                                    tapLogicalY,
                                    transform,
                                    layer
                                )
                            }

                        if (hitLayer != null) {
                            down.consume()
                            if (hitLayer.id != currentSelectedId) {
                                viewModel.selectLayer(hitLayer.id)
                            }
                        }

                        var zoomAccum = 1f
                        var rotationAccum = 0f
                        var panAccum = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val zoomChange = event.calculateZoom()
                                val rotationChange = event.calculateRotation()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoomAccum *= zoomChange
                                    rotationAccum += rotationChange
                                    panAccum += panChange

                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1 - zoomAccum) * centroidSize
                                    val rotationMotion = abs(rotationAccum * Math.PI.toFloat() * centroidSize / 180f)
                                    val panMotion = panAccum.getDistance()

                                    if (zoomMotion > touchSlop || rotationMotion > touchSlop || panMotion > touchSlop) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop) {
                                    val activeId = hitLayer?.id ?: currentSelectedId
                                    if (activeId != null) {
                                        viewModel.updateSelectedTransformRealtime(
                                            deltaX = panChange.x / canvasScale,
                                            deltaY = panChange.y / canvasScale,
                                            scaleMultiplier = zoomChange,
                                            rotationDelta = rotationChange
                                        )
                                        event.changes.forEach {
                                            if (it.positionChanged()) {
                                                it.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })

                        if (pastTouchSlop) {
                            viewModel.commitSelectedTransform()
                        } else if (hitLayer == null) {
                            viewModel.selectLayer(null)
                        }
                    }
                }
            }
    ) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasScale = canvasWidthPx / state.canvasWidth.toFloat()

        sortedLayers.forEach { layer ->
            key(layer.id) {
                val isSelected = layer.id == state.selectedLayerId
                val transform = viewModel.getTransform(layer.id)

                if (state.isCropping && isSelected && state.cropDraft != null) {
                    CropOverlayItem(
                        layer = layer,
                        draftCrop = state.cropDraft!!,
                        transform = transform,
                        canvasScale = canvasScale
                    )
                } else {
                    LayerItem(
                        layer = layer,
                        transform = transform,
                        isSelected = isSelected,
                        canvasScale = canvasScale,
                        dashPathEffect = dashPathEffect
                    )
                }
            }
        }
    }
}

@Composable
private fun LayerItem(
    layer: CompositionLayer,
    transform: LayerTransformState,
    isSelected: Boolean,
    canvasScale: Float,
    dashPathEffect: PathEffect
) {
    val context = LocalContext.current
    val imageRequest = remember(
        layer.uri,
        layer.maskUri,
        layer.isMaskInverted,
        layer.isBlurEnabled,
        layer.blurRadius,
        layer.blurMaskUri,
        layer.isBlurMaskInverted
    ) {
        val builder = ImageRequest.Builder(context)
            .data(layer.uri)
            .size(512)
            .crossfade(false)
        if (layer.maskUri != null || (layer.isBlurEnabled && layer.blurRadius > 0f && layer.blurMaskUri != null)) {
            builder.transformations(
                MaskTransformation(
                    context = context,
                    maskUri = layer.maskUri,
                    isInverted = layer.isMaskInverted,
                    isBlurEnabled = layer.isBlurEnabled,
                    blurRadius = layer.blurRadius,
                    blurMaskUri = layer.blurMaskUri,
                    isBlurMaskInverted = layer.isBlurMaskInverted
                )
            )
        }
        builder.build()
    }

    val crop = layer.crop
    val cropW = crop.widthRatio
    val cropH = crop.heightRatio

    val visibleDisplayW = remember(layer.intrinsicWidth, cropW, canvasScale) {
        layer.intrinsicWidth * cropW * canvasScale
    }
    val visibleDisplayH = remember(layer.intrinsicHeight, cropH, canvasScale) {
        layer.intrinsicHeight * cropH * canvasScale
    }
    val fullDisplayW = remember(layer.intrinsicWidth, canvasScale) {
        layer.intrinsicWidth * canvasScale
    }
    val fullDisplayH = remember(layer.intrinsicHeight, canvasScale) {
        layer.intrinsicHeight * canvasScale
    }

    val visibleWDp = with(LocalDensity.current) { visibleDisplayW.toDp() }
    val visibleHDp = with(LocalDensity.current) { visibleDisplayH.toDp() }
    val fullWDp = with(LocalDensity.current) { fullDisplayW.toDp() }
    val fullHDp = with(LocalDensity.current) { fullDisplayH.toDp() }

    Box(
        modifier = Modifier
            .size(width = visibleWDp, height = visibleHDp)
            .graphicsLayer {
                val centerX = transform.x * canvasScale
                val centerY = transform.y * canvasScale
                translationX = centerX - (visibleDisplayW / 2f)
                translationY = centerY - (visibleDisplayH / 2f)
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotation
                alpha = transform.opacity
            }
            .clipToBounds()
            .drawBehind {
                if (isSelected) {
                    val strokeWidth = 2.dp.toPx()
                    drawRect(
                        color = Color(0xFF64B5F6),
                        topLeft = Offset.Zero,
                        size = size,
                        style = Stroke(
                            width = strokeWidth,
                            pathEffect = dashPathEffect
                        )
                    )
                    val handleRadius = 4.dp.toPx()
                    drawCircle(Color.White, handleRadius, Offset(0f, 0f))
                    drawCircle(Color.White, handleRadius, Offset(size.width, 0f))
                    drawCircle(Color.White, handleRadius, Offset(0f, size.height))
                    drawCircle(Color.White, handleRadius, Offset(size.width, size.height))
                }
            }
    ) {
        Box(
            modifier = Modifier
                .requiredSize(width = fullWDp, height = fullHDp)
                .graphicsLayer {
                    translationX = -crop.left * fullDisplayW
                    translationY = -crop.top * fullDisplayH
                }
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = "Composition Layer",
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun CropOverlayItem(
    layer: CompositionLayer,
    draftCrop: NormalizedCropRect,
    transform: LayerTransformState,
    canvasScale: Float
) {
    val context = LocalContext.current
    val imageRequest = remember(layer.uri) {
        ImageRequest.Builder(context)
            .data(layer.uri)
            .size(512)
            .crossfade(false)
            .build()
    }

    val fullDisplayW = layer.intrinsicWidth * canvasScale
    val fullDisplayH = layer.intrinsicHeight * canvasScale
    val fullWDp = with(LocalDensity.current) { fullDisplayW.toDp() }
    val fullHDp = with(LocalDensity.current) { fullDisplayH.toDp() }

    Box(
        modifier = Modifier
            .size(width = fullWDp, height = fullHDp)
            .graphicsLayer {
                val centerX = transform.x * canvasScale
                val centerY = transform.y * canvasScale
                translationX = centerX - (fullDisplayW / 2f)
                translationY = centerY - (fullDisplayH / 2f)
                scaleX = transform.scale
                scaleY = transform.scale
                rotationZ = transform.rotation
            }
    ) {
        AsyncImage(
            model = imageRequest,
            contentDescription = "Cropping Layer",
            contentScale = ContentScale.FillBounds,
            modifier = Modifier.fillMaxSize()
        )

        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            val cLeft = draftCrop.left * w
            val cTop = draftCrop.top * h
            val cRight = draftCrop.right * w
            val cBottom = draftCrop.bottom * h
            val cWidth = cRight - cLeft
            val cHeight = cBottom - cTop

            val scrimColor = Color(0x99000000)

            // Scrim outside crop area:
            if (cTop > 0) {
                drawRect(scrimColor, topLeft = Offset(0f, 0f), size = Size(w, cTop))
            }
            if (cBottom < h) {
                drawRect(scrimColor, topLeft = Offset(0f, cBottom), size = Size(w, h - cBottom))
            }
            if (cLeft > 0 && cHeight > 0) {
                drawRect(scrimColor, topLeft = Offset(0f, cTop), size = Size(cLeft, cHeight))
            }
            if (cRight < w && cHeight > 0) {
                drawRect(scrimColor, topLeft = Offset(cRight, cTop), size = Size(w - cRight, cHeight))
            }

            // Crop boundary
            drawRect(
                color = Color.White,
                topLeft = Offset(cLeft, cTop),
                size = Size(cWidth, cHeight),
                style = Stroke(width = 2.dp.toPx())
            )

            // Rule-of-thirds grid
            val gridColor = Color.White.copy(alpha = 0.45f)
            drawLine(gridColor, Offset(cLeft, cTop + cHeight / 3f), Offset(cRight, cTop + cHeight / 3f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(cLeft, cTop + 2f * cHeight / 3f), Offset(cRight, cTop + 2f * cHeight / 3f), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(cLeft + cWidth / 3f, cTop), Offset(cLeft + cWidth / 3f, cBottom), strokeWidth = 1.dp.toPx())
            drawLine(gridColor, Offset(cLeft + 2f * cWidth / 3f, cTop), Offset(cLeft + 2f * cWidth / 3f, cBottom), strokeWidth = 1.dp.toPx())

            // Corner handles (L-shaped bracket marks)
            val bracketLen = 16.dp.toPx().coerceAtMost(cWidth / 3f).coerceAtMost(cHeight / 3f)
            val bracketWidth = 4.dp.toPx()
            val bracketColor = Color.White

            // Top-Left
            drawLine(bracketColor, Offset(cLeft - 1f, cTop), Offset(cLeft + bracketLen, cTop), bracketWidth)
            drawLine(bracketColor, Offset(cLeft, cTop - 1f), Offset(cLeft, cTop + bracketLen), bracketWidth)

            // Top-Right
            drawLine(bracketColor, Offset(cRight + 1f, cTop), Offset(cRight - bracketLen, cTop), bracketWidth)
            drawLine(bracketColor, Offset(cRight, cTop - 1f), Offset(cRight, cTop + bracketLen), bracketWidth)

            // Bottom-Left
            drawLine(bracketColor, Offset(cLeft - 1f, cBottom), Offset(cLeft + bracketLen, cBottom), bracketWidth)
            drawLine(bracketColor, Offset(cLeft, cBottom + 1f), Offset(cLeft, cBottom - bracketLen), bracketWidth)

            // Bottom-Right
            drawLine(bracketColor, Offset(cRight + 1f, cBottom), Offset(cRight - bracketLen, cBottom), bracketWidth)
            drawLine(bracketColor, Offset(cRight, cBottom + 1f), Offset(cRight, cBottom - bracketLen), bracketWidth)

            // Edge midpoint dots
            val dotRadius = 3.5.dp.toPx()
            drawCircle(bracketColor, dotRadius, Offset(cLeft + cWidth / 2f, cTop))
            drawCircle(bracketColor, dotRadius, Offset(cLeft + cWidth / 2f, cBottom))
            drawCircle(bracketColor, dotRadius, Offset(cLeft, cTop + cHeight / 2f))
            drawCircle(bracketColor, dotRadius, Offset(cRight, cTop + cHeight / 2f))
        }
    }
}

/**
 * Mathematical hit-test for rotated and scaled layer, accounting for crop rectangle.
 */
private fun isPointInsideLayer(
    px: Float,
    py: Float,
    transform: LayerTransformState,
    layer: CompositionLayer
): Boolean {
    val dx = px - transform.x
    val dy = py - transform.y

    val rad = Math.toRadians(-transform.rotation.toDouble())
    val cosVal = cos(rad)
    val sinVal = sin(rad)
    val unrotX = (dx * cosVal - dy * sinVal).toFloat()
    val unrotY = (dx * sinVal + dy * cosVal).toFloat()

    val cropW = layer.crop.widthRatio
    val cropH = layer.crop.heightRatio
    val halfW = (layer.intrinsicWidth * cropW * transform.scale) / 2f
    val halfH = (layer.intrinsicHeight * cropH * transform.scale) / 2f

    return abs(unrotX) <= halfW && abs(unrotY) <= halfH
}

@Composable
private fun CropControlsBar(
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("crop_controls_bar"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Crop,
                        contentDescription = "Crop",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Crop Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Drag handles or box",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).testTag("crop_cancel_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Cancel", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onReset,
                    modifier = Modifier.weight(1f).testTag("crop_reset_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Full", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Full", fontSize = 12.sp)
                }

                Button(
                    onClick = onApply,
                    modifier = Modifier.weight(1f).testTag("crop_apply_button"),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Apply", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Apply", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LayerEditPanel(
    selectedLayer: CompositionLayer,
    viewModel: CompositionViewModel,
    onStartCrop: () -> Unit,
    onMoveForward: () -> Unit,
    onMoveBackward: () -> Unit,
    onBringToFront: () -> Unit,
    onSendToBack: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var currentTool by remember(selectedLayer.id) { mutableStateOf(EditTool.OPACITY) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("layer_edit_panel"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Tool selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                EditTool.values().forEach { tool ->
                    FilterChip(
                        selected = currentTool == tool,
                        onClick = {
                            if (tool == EditTool.CROP) {
                                onStartCrop()
                            } else {
                                currentTool = tool
                            }
                        },
                        leadingIcon = {
                            when (tool) {
                                EditTool.OPACITY -> Icon(Icons.Default.Opacity, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.ROTATE -> Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.ALIGN -> Icon(Icons.Default.AlignHorizontalCenter, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.CROP -> Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.MASK -> Icon(Icons.Default.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.BLUR -> Icon(Icons.Default.BlurOn, contentDescription = null, modifier = Modifier.size(16.dp))
                                EditTool.ORDER -> Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        label = { Text(tool.label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("tool_chip_${tool.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tool-specific controls
            when (currentTool) {
                EditTool.OPACITY -> {
                    val opacityPercent = (selectedLayer.opacity * 100f).roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Opacity: $opacityPercent%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { viewModel.resetSelectedOpacity() },
                            modifier = Modifier.size(32.dp).testTag("opacity_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Opacity to 100%",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Slider(
                        value = selectedLayer.opacity,
                        onValueChange = { viewModel.updateSelectedOpacityRealtime(it) },
                        onValueChangeFinished = { viewModel.commitSelectedTransform() },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().testTag("opacity_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { preset ->
                            val pct = (preset * 100).toInt()
                            OutlinedButton(
                                onClick = { viewModel.setSelectedOpacity(preset) },
                                modifier = Modifier.weight(1f).testTag("opacity_preset_${pct}"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                            ) {
                                Text("$pct%", fontSize = 11.sp)
                            }
                        }
                    }
                }

                EditTool.ROTATE -> {
                    val angleDeg = selectedLayer.rotation.roundToInt()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Rotation: $angleDeg°",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { viewModel.resetSelectedRotation() },
                            modifier = Modifier.size(32.dp).testTag("rotation_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = "Reset Rotation to 0°",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Slider from -180 to 180
                    val sliderVal = if (selectedLayer.rotation > 180f) selectedLayer.rotation - 360f else selectedLayer.rotation
                    Slider(
                        value = sliderVal,
                        onValueChange = { viewModel.updateSelectedRotationRealtime(it) },
                        onValueChangeFinished = { viewModel.commitSelectedTransform() },
                        valueRange = -180f..180f,
                        modifier = Modifier.fillMaxWidth().testTag("rotation_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.rotateSelectedBy(-90f) },
                            modifier = Modifier.weight(1f).testTag("rotate_minus_90_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RotateLeft, contentDescription = "-90°", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("-90°", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.rotateSelectedBy(90f) },
                            modifier = Modifier.weight(1f).testTag("rotate_plus_90_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.RotateRight, contentDescription = "+90°", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("+90°", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.rotateSelectedBy(180f) },
                            modifier = Modifier.weight(1f).testTag("rotate_180_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("180°", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetSelectedRotation() },
                            modifier = Modifier.weight(1f).testTag("rotate_0_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Text("0°", fontSize = 11.sp)
                        }
                    }
                }

                EditTool.ALIGN -> {
                    Text(
                        text = "Canvas Alignment (1024 × 1024)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Horizontal alignments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.LEFT) },
                            modifier = Modifier.weight(1f).testTag("align_left_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignHorizontalLeft, contentDescription = "Align Left", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Left", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.CENTER_HORIZONTAL) },
                            modifier = Modifier.weight(1f).testTag("align_center_h_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignHorizontalCenter, contentDescription = "Center H", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Center", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.RIGHT) },
                            modifier = Modifier.weight(1f).testTag("align_right_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignHorizontalRight, contentDescription = "Align Right", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Right", fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Vertical alignments
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.TOP) },
                            modifier = Modifier.weight(1f).testTag("align_top_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignVerticalTop, contentDescription = "Align Top", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Top", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.CENTER_VERTICAL) },
                            modifier = Modifier.weight(1f).testTag("align_center_v_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignVerticalCenter, contentDescription = "Center V", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Center", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.alignSelected(AlignmentType.BOTTOM) },
                            modifier = Modifier.weight(1f).testTag("align_bottom_button"),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.AlignVerticalBottom, contentDescription = "Align Bottom", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Bottom", fontSize = 11.sp)
                        }
                    }
                }

                EditTool.CROP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onStartCrop,
                            modifier = Modifier.weight(1f).testTag("enter_crop_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Enter Crop Mode", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = { viewModel.resetSelectedCrop() },
                            modifier = Modifier.testTag("reset_crop_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset Crop", fontSize = 12.sp)
                        }
                    }
                }

                EditTool.ORDER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Layer Order:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onBringToFront,
                                modifier = Modifier.size(36.dp).testTag("layer_bring_to_front_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignTop,
                                    contentDescription = "Bring to front",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveForward,
                                modifier = Modifier.size(36.dp).testTag("layer_move_forward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Move forward",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onMoveBackward,
                                modifier = Modifier.size(36.dp).testTag("layer_move_backward_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Move backward",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onSendToBack,
                                modifier = Modifier.size(36.dp).testTag("layer_send_to_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.VerticalAlignBottom,
                                    contentDescription = "Send to back",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                EditTool.MASK -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (selectedLayer.hasMask) {
                                    if (selectedLayer.isMaskInverted) "Mask: Active (Inverted)" else "Mask: Active"
                                } else {
                                    "Mask: None"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedLayer.hasMask) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedLayer.hasMask) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.invertSelectedLayerMask() },
                                        modifier = Modifier.size(32.dp).testTag("invert_mask_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flip,
                                            contentDescription = "Invert Mask",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearSelectedLayerMask() },
                                        modifier = Modifier.size(32.dp).testTag("clear_mask_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove Mask",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openMaskEditor(selectedLayer.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("open_mask_editor_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Brush, contentDescription = "Edit Mask", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedLayer.hasMask) "Edit Mask" else "Create Mask", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.removeBackgroundForSelectedLayer() },
                                enabled = !state.isProcessingBackgroundRemoval,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("remove_background_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (state.isProcessingBackgroundRemoval) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Processing...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Remove Background", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Remove BG", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                EditTool.BLUR -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = selectedLayer.isBlurEnabled,
                                    onCheckedChange = { enabled ->
                                        viewModel.setLayerBlurEnabled(selectedLayer.id, enabled)
                                    },
                                    modifier = Modifier.testTag("blur_enable_switch")
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedLayer.isBlurEnabled) "Privacy Blur: On" else "Privacy Blur: Off",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (selectedLayer.isBlurEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (selectedLayer.hasBlurMask) {
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = { viewModel.invertSelectedLayerBlurMask() },
                                        modifier = Modifier.size(32.dp).testTag("invert_blur_mask_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Flip,
                                            contentDescription = "Invert Blur Region",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.clearSelectedLayerBlurMask() },
                                        modifier = Modifier.size(32.dp).testTag("clear_blur_mask_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear Blur Region",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Blur Radius Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.BlurOn,
                                contentDescription = "Blur Radius",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = selectedLayer.blurRadius,
                                onValueChange = { radius ->
                                    viewModel.setLayerBlurRadius(selectedLayer.id, radius)
                                },
                                valueRange = 2f..50f,
                                enabled = selectedLayer.isBlurEnabled,
                                modifier = Modifier.weight(1f).testTag("blur_radius_slider")
                            )
                            Text(
                                text = "${selectedLayer.blurRadius.toInt()}px",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(36.dp)
                            )
                        }

                        // Action buttons: Paint Blur Region & Reset
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.openBlurMaskEditor(selectedLayer.id) },
                                enabled = selectedLayer.isBlurEnabled,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .testTag("open_blur_region_editor_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.Brush, contentDescription = "Paint Blur Region", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (selectedLayer.hasBlurMask) "Edit Blur Region" else "Paint Blur Region", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.resetSelectedLayerBlur() },
                                modifier = Modifier
                                    .height(40.dp)
                                    .testTag("reset_blur_button"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(Icons.Default.RestartAlt, contentDescription = "Reset Blur", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                thickness = 1.dp
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Bottom bar with layer info & delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${selectedLayer.intrinsicWidth} × ${selectedLayer.intrinsicHeight} px",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.testTag("layer_delete_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete layer",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete Layer", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun CompositionThumbnailStrip(
    layers: List<CompositionLayer>,
    selectedLayerId: String?,
    onSelectLayer: (String) -> Unit,
    onDeleteLayer: (String) -> Unit,
    onAddImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
    ) {
        itemsIndexed(
            items = layers,
            key = { _, layer -> layer.id }
        ) { index, layer ->
            val isSelected = layer.id == selectedLayerId
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            }
            val borderWidth = if (isSelected) 2.dp else 1.dp

            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Select layer ${index + 1}",
                        onClick = { onSelectLayer(layer.id) }
                    )
                    .testTag("thumbnail_layer_${layer.id}")
            ) {
                val thumbRequest = remember(layer.uri) {
                    ImageRequest.Builder(context)
                        .data(layer.uri)
                        .crossfade(false)
                        .size(160)
                        .build()
                }
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = "Layer ${index + 1} thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                // Layer number badge
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(topEnd = 6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "#${index + 1}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }

                // Delete 'X' button
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(22.dp)
                        .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Delete layer ${index + 1}",
                            onClick = { onDeleteLayer(layer.id) }
                        )
                        .testTag("delete_layer_${layer.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete layer ${index + 1}",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        item(key = "add_more_composition_images_btn") {
            Box(
                modifier = Modifier
                    .size(width = 80.dp, height = 80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        RoundedCornerShape(10.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Add layer",
                        onClick = onAddImage
                    )
                    .testTag("composition_add_layer_card"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add image",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

private fun shareCompositionFile(
    context: Context,
    file: File,
    format: ExportFormat
) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share composition"))
    } catch (e: Exception) {
        // Fallback
    }
}
