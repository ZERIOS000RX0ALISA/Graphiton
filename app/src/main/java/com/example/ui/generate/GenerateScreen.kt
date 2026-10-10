package com.example.ui.generate

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.domain.model.GenerationMode
import com.example.domain.model.GenerationState
import com.example.domain.model.SelectedImage
import com.example.ui.components.ConnectionBadge
import com.example.ui.components.ResolutionWarningDialog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StatusWarning
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GenerateScreen(
    viewModel: GenerateViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToComposition: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiParams by viewModel.uiParams.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    // Multi-image Photo picker for image input
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addSelectedImages(uris)
        }
    }

    if (uiParams.showWarningDialog) {
        ResolutionWarningDialog(
            width = uiParams.width,
            height = uiParams.height,
            onDismiss = { viewModel.dismissWarningDialog() },
            onConfirm = { viewModel.proceedWithLargeResolution() }
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Header with Connection Badge
        item {
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
                        text = "Local Stable Diffusion Client",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                ConnectionBadge(
                    status = connectionState.status,
                    detectedModel = connectionState.detectedModel,
                    onClick = onNavigateToSettings
                )
            }
        }

        // 2. Mode Tabs: Text-to-Image | Image Edit | Composition
        item {
            TabRow(
                selectedTabIndex = if (uiParams.mode == GenerationMode.TEXT_TO_IMAGE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = uiParams.mode == GenerationMode.TEXT_TO_IMAGE,
                    onClick = { viewModel.setMode(GenerationMode.TEXT_TO_IMAGE) },
                    text = { Text("Text to Image", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_txt2img")
                )
                Tab(
                    selected = uiParams.mode == GenerationMode.IMAGE_TO_IMAGE,
                    onClick = { viewModel.setMode(GenerationMode.IMAGE_TO_IMAGE) },
                    text = { Text("Image Edit", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_img2img")
                )
                Tab(
                    selected = false,
                    onClick = onNavigateToComposition,
                    text = { Text("Composition", fontWeight = FontWeight.SemiBold) },
                    modifier = Modifier.testTag("tab_composition")
                )
            }
        }

        // 3. Image Preview Area
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("image_preview_box"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        // In Progress State
                        generationState is GenerationState.Generating ||
                        generationState is GenerationState.Connecting ||
                        generationState is GenerationState.SendingRequest ||
                        generationState is GenerationState.ReceivingImage -> {
                            val state = generationState
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 3.dp,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))

                                val statusLabel = when (state) {
                                    is GenerationState.Connecting -> "Connecting to server..."
                                    is GenerationState.SendingRequest -> "Sending request to Linux PC..."
                                    is GenerationState.Generating -> {
                                        val s = state.elapsedSeconds
                                        if (s >= 60) {
                                            "Generating on PC (${s / 60}m ${s % 60}s)..."
                                        } else {
                                            "Generating on PC (${s}s)..."
                                        }
                                    }
                                    is GenerationState.ReceivingImage -> "Receiving & decoding image..."
                                    else -> "Processing..."
                                }

                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Medium
                                )

                                if (state is GenerationState.Generating) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = if (state.elapsedSeconds >= 45) {
                                            "Generating on CPU: CPU inference can take 10+ minutes.\nThe request will remain active until completion."
                                        } else {
                                            "The Linux PC is running stable-diffusion.cpp on CPU."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Completed / Success State or Existing Result
                        uiParams.currentResult != null -> {
                            val result = uiParams.currentResult!!
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = File(result.imagePath),
                                    contentDescription = "Generated image: ${result.prompt}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Action Buttons overlay (Save & Share)
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(12.dp)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(Color.Black.copy(alpha = 0.7f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { viewModel.saveCurrentImage() },
                                        modifier = Modifier.size(36.dp).testTag("save_image_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Download,
                                            contentDescription = "Save to Gallery",
                                            tint = Color.White
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = {
                                            try {
                                                val file = File(result.imagePath)
                                                val uri = FileProvider.getUriForFile(
                                                    context,
                                                    "${context.packageName}.fileprovider",
                                                    file
                                                )
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "Share generated image"))
                                            } catch (e: Exception) {
                                                // Fallback simple share
                                            }
                                        },
                                        modifier = Modifier.size(36.dp).testTag("share_image_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = Color.White
                                        )
                                    }
                                }

                                // Dimension tag overlay
                                Surface(
                                    color = Color.Black.copy(alpha = 0.65f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = "${result.width}×${result.height} • ${result.sampler} • ${result.durationMs / 1000}s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        // Error State
                        generationState is GenerationState.Error -> {
                            val error = generationState as GenerationState.Error
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(20.dp)
                            ) {
                                Text(
                                    text = "Generation Failed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Empty Placeholder
                        else -> {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Image,
                                    contentDescription = "Preview placeholder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (uiParams.mode == GenerationMode.IMAGE_TO_IMAGE) "Select an image below to edit" else "Generated image will appear here",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. Image-to-Image Source Image Selector (if in img2img mode)
        if (uiParams.mode == GenerationMode.IMAGE_TO_IMAGE) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Source Images",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiParams.selectedImages.isNotEmpty()) {
                                TextButton(
                                    onClick = { viewModel.clearAllSelectedImages() },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Clear all",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }

                        if (uiParams.selectedImages.isNotEmpty()) {
                            val count = uiParams.selectedImages.size
                            Text(
                                text = if (count == 1) {
                                    "1 image selected • Ready for Image-to-Image edit"
                                } else {
                                    "$count images selected • Tap to set primary (composition mode pending)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (count > 1) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        if (uiParams.selectedImages.isEmpty()) {
                            Surface(
                                onClick = {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("select_source_image_button"),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 18.dp, horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddPhotoAlternate,
                                        contentDescription = "Add image",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "+ Add image",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        } else {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("selected_images_lazy_row"),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
                            ) {
                                itemsIndexed(
                                    items = uiParams.selectedImages,
                                    key = { _, item -> item.id }
                                ) { index, image ->
                                    SelectedImageCard(
                                        image = image,
                                        index = index,
                                        isPrimary = image.isPrimary,
                                        onSelectPrimary = { viewModel.setPrimaryImage(image.id) },
                                        onRemove = { viewModel.removeSelectedImage(image.id) }
                                    )
                                }

                                item(key = "add_more_images_end_button") {
                                    AddImageCard(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Strength Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Denoising Strength: ${"%.2f".format(uiParams.strength)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Slider(
                            value = uiParams.strength,
                            onValueChange = { viewModel.updateStrength(it) },
                            valueRange = 0.1f..1.0f,
                            steps = 17,
                            modifier = Modifier.testTag("strength_slider")
                        )
                        Text(
                            text = "Lower strength = preserve more of original. Higher = larger changes.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // 5. Prompt Input Field
        item {
            Column {
                Text(
                    text = "Prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiParams.prompt,
                    onValueChange = { viewModel.updatePrompt(it) },
                    placeholder = { Text("e.g. a small red apple on a wooden table") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 84.dp)
                        .testTag("prompt_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    maxLines = 4
                )
            }
        }

        // 6. Negative Prompt Input Field
        item {
            Column {
                Text(
                    text = "Negative Prompt",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = uiParams.negativePrompt,
                    onValueChange = { viewModel.updateNegativePrompt(it) },
                    placeholder = { Text("blurry, low quality, artifacts") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .testTag("negative_prompt_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    maxLines = 3
                )
            }
        }

        // 7. Resolution Presets
        item {
            Column {
                Text(
                    text = "Resolution Presets",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))

                val presets = listOf(
                    Pair(512, 512),
                    Pair(512, 768),
                    Pair(768, 512),
                    Pair(640, 480),
                    Pair(480, 640)
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    presets.forEach { (w, h) ->
                        val isSelected = uiParams.width == w && uiParams.height == h
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.setPreset(w, h) },
                            label = { Text("${w}×${h}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }
        }

        // 8. Advanced Parameters (Collapsible)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleParametersDrawer() },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Parameters (${uiParams.width}×${uiParams.height}, ${uiParams.steps} steps, ${uiParams.samplerName})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Icon(
                            imageVector = if (uiParams.showParametersDrawer) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (uiParams.showParametersDrawer) "Collapse" else "Expand"
                        )
                    }

                    AnimatedVisibility(visible = uiParams.showParametersDrawer) {
                        Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Steps Slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Steps", style = MaterialTheme.typography.bodySmall)
                                    Text("${uiParams.steps}", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = uiParams.steps.toFloat(),
                                    onValueChange = { viewModel.updateSteps(it.toInt()) },
                                    valueRange = 10f..50f,
                                    steps = 7,
                                    modifier = Modifier.testTag("steps_slider")
                                )
                            }

                            // Seed Input with Randomize button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = uiParams.seed.toString(),
                                    onValueChange = { str ->
                                        str.toLongOrNull()?.let { viewModel.updateSeed(it) }
                                    },
                                    label = { Text("Seed") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("seed_input"),
                                    shape = RoundedCornerShape(8.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = { viewModel.randomizeSeed() },
                                    modifier = Modifier.testTag("random_seed_button")
                                ) {
                                    Icon(Icons.Default.Casino, contentDescription = "Randomize seed")
                                }
                            }

                            // Sampler dropdown
                            var samplerExpanded by remember { mutableStateOf(false) }
                            val availableSamplers = remember(connectionState.samplers) {
                                if (connectionState.samplers.isNotEmpty()) {
                                    connectionState.samplers.map { it.name }
                                } else {
                                    listOf("euler", "euler_a", "dpm++_2m", "dpm++_sde", "ddim", "heun")
                                }
                            }

                            ExposedDropdownMenuBox(
                                expanded = samplerExpanded,
                                onExpandedChange = { samplerExpanded = !samplerExpanded }
                            ) {
                                OutlinedTextField(
                                    value = uiParams.samplerName,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Sampler") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = samplerExpanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                ExposedDropdownMenu(
                                    expanded = samplerExpanded,
                                    onDismissRequest = { samplerExpanded = false }
                                ) {
                                    availableSamplers.forEach { sampler ->
                                        DropdownMenuItem(
                                            text = { Text(sampler) },
                                            onClick = {
                                                viewModel.updateSampler(sampler)
                                                samplerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 9. Generate Button
        item {
            val isGenerating = generationState is GenerationState.Generating ||
                    generationState is GenerationState.Connecting ||
                    generationState is GenerationState.SendingRequest ||
                    generationState is GenerationState.ReceivingImage

            Button(
                onClick = { viewModel.onGenerateClicked() },
                enabled = !isGenerating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("generate_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Generating on PC...",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiParams.mode == GenerationMode.IMAGE_TO_IMAGE) "Generate Edit" else "Generate Image",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedImageCard(
    image: SelectedImage,
    index: Int,
    isPrimary: Boolean,
    onSelectPrimary: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val borderColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    }
    val borderWidth = if (isPrimary) 2.dp else 1.dp

    Box(
        modifier = modifier
            .size(width = 88.dp, height = 88.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .clickable(
                role = Role.Button,
                onClickLabel = "Select image ${index + 1} as primary",
                onClick = onSelectPrimary
            )
            .testTag("selected_image_card_${image.id}")
    ) {
        // Thumbnail loaded efficiently with downsampled target size to prevent RAM problems
        val cardRequest = remember(image.uri) {
            ImageRequest.Builder(context)
                .data(image.uri)
                .crossfade(false)
                .size(256)
                .build()
        }
        AsyncImage(
            model = cardRequest,
            contentDescription = "Selected image ${index + 1}${if (isPrimary) " (Primary)" else ""}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Primary badge indicator
        if (isPrimary) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topEnd = 6.dp)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "PRIMARY",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // Small remove "X" button
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.75f), CircleShape)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Remove image ${index + 1}",
                    onClick = onRemove
                )
                .testTag("remove_image_${image.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove image ${index + 1}",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddImageCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(width = 88.dp, height = 88.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                RoundedCornerShape(10.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable(
                role = Role.Button,
                onClickLabel = "Add image",
                onClick = onClick
            )
            .testTag("add_image_strip_button"),
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
                modifier = Modifier.size(28.dp)
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
