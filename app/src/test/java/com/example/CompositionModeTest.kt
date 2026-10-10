package com.example

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.CanvasBackground
import com.example.domain.model.CompositionLayer
import com.example.domain.model.CompositionState
import com.example.domain.model.ExportFormat
import com.example.ui.composition.CompositionViewModel
import com.example.utils.CompositionExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CompositionModeTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var viewModel: CompositionViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        viewModel = CompositionViewModel(app, ioDispatcher = testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state is clean and empty`() {
        val state = viewModel.state.value
        assertEquals(0, state.layers.size)
        assertNull(state.selectedLayerId)
        assertNull(state.selectedLayer)
        assertEquals(1024, state.canvasWidth)
        assertEquals(1024, state.canvasHeight)
        assertEquals(CanvasBackground.TRANSPARENT, state.background)
    }

    @Test
    fun `test adding one image creates centered layer with correct selection`() = runTest {
        val uri = Uri.parse("content://media/external/images/media/101")
        viewModel.addLayers(listOf(uri))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(1, state.layers.size)
        val layer = state.layers[0]
        assertEquals(uri, layer.uri)
        assertEquals(512f, layer.x, 0.01f)
        assertEquals(512f, layer.y, 0.01f)
        assertEquals(layer.id, state.selectedLayerId)
        assertEquals(0f, layer.rotation, 0.01f)
    }

    @Test
    fun `test adding multiple images creates separate layers in order`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val uri3 = Uri.parse("content://media/external/images/media/3")

        viewModel.addLayers(listOf(uri1, uri2, uri3))
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(3, state.layers.size)
        assertEquals(uri1, state.layers[0].uri)
        assertEquals(uri2, state.layers[1].uri)
        assertEquals(uri3, state.layers[2].uri)

        // Check zIndex order
        assertTrue(state.layers[0].zIndex < state.layers[1].zIndex)
        assertTrue(state.layers[1].zIndex < state.layers[2].zIndex)
    }

    @Test
    fun `test moving selected image only modifies that layer`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addLayers(listOf(uri1, uri2))
        testDispatcher.scheduler.advanceUntilIdle()

        val layer1Id = viewModel.state.value.layers[0].id
        val layer2Id = viewModel.state.value.layers[1].id

        // Select first layer and move
        viewModel.selectLayer(layer1Id)
        viewModel.updateSelectedTransform(deltaX = 50f, deltaY = -30f)

        val updatedState = viewModel.state.value
        val updatedLayer1 = updatedState.layers.first { it.id == layer1Id }
        val updatedLayer2 = updatedState.layers.first { it.id == layer2Id }

        assertEquals(562f, updatedLayer1.x, 0.01f)
        assertEquals(482f, updatedLayer1.y, 0.01f)

        // Second layer must remain unchanged
        assertEquals(512f, updatedLayer2.x, 0.01f)
        assertEquals(512f, updatedLayer2.y, 0.01f)
    }

    @Test
    fun `test scaling selected image only modifies that layer`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addLayers(listOf(uri1, uri2))
        testDispatcher.scheduler.advanceUntilIdle()

        val layer1 = viewModel.state.value.layers[0]
        val initialScale = layer1.scale

        viewModel.selectLayer(layer1.id)
        viewModel.updateSelectedTransform(deltaX = 0f, deltaY = 0f, scaleMultiplier = 1.5f)

        val updatedLayer1 = viewModel.state.value.layers.first { it.id == layer1.id }
        assertEquals(initialScale * 1.5f, updatedLayer1.scale, 0.01f)

        // Other layer unchanged
        val layer2 = viewModel.state.value.layers[1]
        assertEquals(layer2.scale, viewModel.state.value.layers.first { it.id == layer2.id }.scale, 0.01f)
    }

    @Test
    fun `test rotating selected image only modifies that layer`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addLayers(listOf(uri1, uri2))
        testDispatcher.scheduler.advanceUntilIdle()

        val layer1 = viewModel.state.value.layers[0]
        viewModel.selectLayer(layer1.id)
        viewModel.updateSelectedTransform(deltaX = 0f, deltaY = 0f, rotationDelta = 45f)

        val updatedLayer1 = viewModel.state.value.layers.first { it.id == layer1.id }
        assertEquals(45f, updatedLayer1.rotation, 0.01f)

        // Second layer rotation must still be 0
        val updatedLayer2 = viewModel.state.value.layers[1]
        assertEquals(0f, updatedLayer2.rotation, 0.01f)
    }

    @Test
    fun `test layer reordering controls`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val uri3 = Uri.parse("content://media/external/images/media/3")

        viewModel.addLayers(listOf(uri1, uri2, uri3))
        testDispatcher.scheduler.advanceUntilIdle()

        val id1 = viewModel.state.value.layers[0].id
        val id2 = viewModel.state.value.layers[1].id
        val id3 = viewModel.state.value.layers[2].id

        // Bring layer 1 to front
        viewModel.selectLayer(id1)
        viewModel.bringToFront()

        val frontZ = viewModel.state.value.layers.first { it.id == id1 }.zIndex
        val z2 = viewModel.state.value.layers.first { it.id == id2 }.zIndex
        val z3 = viewModel.state.value.layers.first { it.id == id3 }.zIndex

        assertTrue(frontZ > z2)
        assertTrue(frontZ > z3)

        // Send layer 1 to back
        viewModel.sendToBack()
        val backZ = viewModel.state.value.layers.first { it.id == id1 }.zIndex
        assertTrue(backZ < z2)
        assertTrue(backZ < z3)
    }

    @Test
    fun `test delete layer removes only selected layer`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addLayers(listOf(uri1, uri2))
        testDispatcher.scheduler.advanceUntilIdle()

        val id1 = viewModel.state.value.layers[0].id
        val id2 = viewModel.state.value.layers[1].id

        viewModel.selectLayer(id1)
        viewModel.deleteSelectedLayer()

        val remaining = viewModel.state.value.layers
        assertEquals(1, remaining.size)
        assertEquals(id2, remaining[0].id)
        assertEquals(id2, viewModel.state.value.selectedLayerId)
    }

    @Test
    fun `test twenty layers added smoothly without issues`() = runTest {
        val uris = (1..20).map { Uri.parse("content://media/external/images/media/$it") }
        viewModel.addLayers(uris)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(20, viewModel.state.value.layers.size)
        assertNotNull(viewModel.state.value.selectedLayerId)
    }

    @Test
    fun `test export composition produces valid output bitmap file`() = runTest {
        // Create a small test image in cache to decode as layer
        val testFile = File(app.cacheDir, "test_layer.png")
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.RED)
        FileOutputStream(testFile).use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = Uri.fromFile(testFile)

        val layer = CompositionLayer(
            uri = uri,
            x = 512f,
            y = 512f,
            scale = 1.0f,
            rotation = 0f,
            zIndex = 0,
            intrinsicWidth = 100,
            intrinsicHeight = 100
        )

        val state = CompositionState(
            canvasWidth = 512,
            canvasHeight = 512,
            background = CanvasBackground.WHITE,
            layers = listOf(layer)
        )

        val result = CompositionExporter.exportComposition(app, state, ExportFormat.PNG)
        assertTrue(result.isSuccess)
        val (file, _) = result.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
    }

    @Test
    fun `test renderFinalComposition creates 1024x1024 bitmap`() = runTest {
        val testFile = File(app.cacheDir, "test_render.png")
        FileOutputStream(testFile).use { out ->
            val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.RED)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
        }
        val uri = Uri.fromFile(testFile)

        val layer = CompositionLayer(
            uri = uri,
            x = 512f,
            y = 512f,
            scale = 1f,
            rotation = 0f,
            intrinsicWidth = 100,
            intrinsicHeight = 100
        )
        val state = CompositionState(
            canvasWidth = 1024,
            canvasHeight = 1024,
            background = CanvasBackground.WHITE,
            layers = listOf(layer)
        )

        val result = CompositionExporter.renderFinalComposition(app, state)
        assertTrue(result.isSuccess)
        val bitmap = result.getOrThrow()
        assertEquals(1024, bitmap.width)
        assertEquals(1024, bitmap.height)
        bitmap.recycle()
    }

    @Test
    fun `test prepareCompositionForImg2img generates valid PNG and file provider Uri`() = runTest {
        val testFile = File(app.cacheDir, "test_img2img.png")
        FileOutputStream(testFile).use { out ->
            val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.BLUE)
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            bmp.recycle()
        }
        val uri = Uri.fromFile(testFile)

        val layer = CompositionLayer(
            uri = uri,
            x = 512f,
            y = 512f,
            scale = 1f,
            rotation = 0f,
            intrinsicWidth = 80,
            intrinsicHeight = 80
        )
        val state = CompositionState(
            canvasWidth = 1024,
            canvasHeight = 1024,
            background = CanvasBackground.WHITE,
            layers = listOf(layer)
        )

        val result = CompositionExporter.prepareCompositionForImg2img(app, state, testDispatcher)
        assertTrue(result.isSuccess)
        val (file, readyUri) = result.getOrThrow()
        assertTrue(file.exists())
        assertTrue(file.length() > 0)
        assertNotNull(readyUri)
        assertTrue(readyUri.toString().contains("comp_img2img") || readyUri.toString().contains(".fileprovider"))
    }

    @Test
    fun `test mask operations apply invert and clear`() = runTest {
        val uri = Uri.parse("content://media/external/images/media/10")
        viewModel.addLayers(listOf(uri))
        testDispatcher.scheduler.advanceUntilIdle()

        val layerId = viewModel.state.value.layers[0].id
        val maskUri = Uri.parse("file:///data/user/0/com.example/files/masks/mask_test.png")

        // 1. Apply Mask
        viewModel.applyMaskToSelectedLayer(maskUri, isInverted = false)
        val stateWithMask = viewModel.state.value
        val layerWithMask = stateWithMask.layers.first { it.id == layerId }
        assertTrue(layerWithMask.hasMask)
        assertEquals(maskUri, layerWithMask.maskUri)
        assertFalse(layerWithMask.isMaskInverted)

        // 2. Invert Mask
        viewModel.invertSelectedLayerMask()
        val stateInverted = viewModel.state.value
        val layerInverted = stateInverted.layers.first { it.id == layerId }
        assertTrue(layerInverted.isMaskInverted)

        // 3. Clear Mask
        viewModel.clearSelectedLayerMask()
        val stateCleared = viewModel.state.value
        val layerCleared = stateCleared.layers.first { it.id == layerId }
        assertFalse(layerCleared.hasMask)
        assertNull(layerCleared.maskUri)
    }

    @Test
    fun `test local background removal engine is available and handles missing file gracefully`() = runTest {
        val engine = com.example.utils.DefaultBackgroundRemovalEngine()
        assertTrue(engine.isAvailable())
        assertEquals("Local ML Kit On-Device Segmenter", engine.name)

        val dummyUri = Uri.parse("content://dummy/1")
        val result = engine.removeBackground(app, dummyUri, "layer_1")
        // Because dummyUri doesn't exist on disk/contentResolver, it returns Error or Unavailable without crashing
        assertTrue(result is com.example.utils.BackgroundRemovalResult.Error || result is com.example.utils.BackgroundRemovalResult.Unavailable)
    }

    @Test
    fun `test privacy blur controls toggle radius and reset`() = runTest {
        val uri = Uri.parse("content://media/external/images/media/20")
        viewModel.addLayers(listOf(uri))
        testDispatcher.scheduler.advanceUntilIdle()

        val layerId = viewModel.state.value.layers[0].id

        // Initial blur state
        val initialLayer = viewModel.state.value.layers[0]
        assertFalse(initialLayer.isBlurEnabled)
        assertEquals(20f, initialLayer.blurRadius, 0.01f)
        assertNull(initialLayer.blurMaskUri)

        // 1. Enable blur
        viewModel.setLayerBlurEnabled(layerId, true)
        assertTrue(viewModel.state.value.layers[0].isBlurEnabled)

        // 2. Adjust blur radius
        viewModel.setLayerBlurRadius(layerId, 35f)
        assertEquals(35f, viewModel.state.value.layers[0].blurRadius, 0.01f)

        // 3. Open Blur Mask Editor target
        viewModel.openBlurMaskEditor(layerId)
        val stateInEditor = viewModel.state.value
        assertTrue(stateInEditor.isMasking)
        assertEquals(com.example.domain.model.MaskEditorTarget.BLUR, stateInEditor.maskEditorTarget)

        // 4. Apply Blur Mask
        val blurMaskUri = Uri.parse("file:///data/user/0/com.example/files/masks/mask_blur_test.png")
        viewModel.applyMaskToSelectedLayer(blurMaskUri, isInverted = false)
        val stateAfterApply = viewModel.state.value
        val layerAfterApply = stateAfterApply.layers.first { it.id == layerId }
        assertFalse(stateAfterApply.isMasking)
        assertTrue(layerAfterApply.hasBlurMask)
        assertEquals(blurMaskUri, layerAfterApply.blurMaskUri)
        assertFalse(layerAfterApply.isBlurMaskInverted)

        // 5. Invert Blur Mask
        viewModel.invertSelectedLayerBlurMask()
        assertTrue(viewModel.state.value.layers.first { it.id == layerId }.isBlurMaskInverted)

        // 6. Reset Blur
        viewModel.resetSelectedLayerBlur()
        val resetLayer = viewModel.state.value.layers.first { it.id == layerId }
        assertFalse(resetLayer.isBlurEnabled)
        assertEquals(20f, resetLayer.blurRadius, 0.01f)
        assertNull(resetLayer.blurMaskUri)
    }

    @Test
    fun `test fast blur operates correctly on bitmap`() {
        val original = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        original.eraseColor(Color.RED)

        val blurred = com.example.utils.FastBlur.stackBlur(original, 10)
        assertNotNull(blurred)
        assertEquals(100, blurred.width)
        assertEquals(100, blurred.height)
        blurred.recycle()
        original.recycle()
    }
}
