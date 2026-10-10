package com.example

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.domain.model.GenerationMode
import com.example.domain.model.SelectedImage
import com.example.ui.generate.GenerateViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MultiImageSelectionTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: Application
    private lateinit var viewModel: GenerateViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        viewModel = GenerateViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state has no selected images`() {
        val params = viewModel.uiParams.value
        assertTrue(params.selectedImages.isEmpty())
    }

    @Test
    fun `test selecting single image makes it primary and updates sourceImageUri`() = runTest {
        val uri = Uri.parse("content://media/external/images/media/1")
        viewModel.addSelectedImages(listOf(uri))

        val params = viewModel.uiParams.value
        assertEquals(1, params.selectedImages.size)
        assertEquals(uri, params.selectedImages[0].uri)
        assertTrue(params.selectedImages[0].isPrimary)
        assertEquals(uri, params.sourceImageUri)
    }

    @Test
    fun `test selecting multiple images preserves order and primary selection`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val uri3 = Uri.parse("content://media/external/images/media/3")

        viewModel.addSelectedImages(listOf(uri1))
        viewModel.addSelectedImages(listOf(uri2, uri3))

        val params = viewModel.uiParams.value
        assertEquals(3, params.selectedImages.size)
        assertEquals(uri1, params.selectedImages[0].uri)
        assertEquals(uri2, params.selectedImages[1].uri)
        assertEquals(uri3, params.selectedImages[2].uri)

        assertTrue(params.selectedImages[0].isPrimary)
        assertFalse(params.selectedImages[1].isPrimary)
        assertFalse(params.selectedImages[2].isPrimary)
    }

    @Test
    fun `test duplicate images are ignored`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        viewModel.addSelectedImages(listOf(uri1))
        viewModel.addSelectedImages(listOf(uri1))

        assertEquals(1, viewModel.uiParams.value.selectedImages.size)
    }

    @Test
    fun `test large list of images can be added without issue`() = runTest {
        val uris = (1..20).map { Uri.parse("content://media/external/images/media/$it") }
        viewModel.addSelectedImages(uris)

        assertEquals(20, viewModel.uiParams.value.selectedImages.size)
        assertTrue(viewModel.uiParams.value.selectedImages[0].isPrimary)
    }

    @Test
    fun `test removing middle image preserves order and other items`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        val uri3 = Uri.parse("content://media/external/images/media/3")

        viewModel.addSelectedImages(listOf(uri1, uri2, uri3))
        val idToRemove = viewModel.uiParams.value.selectedImages[1].id
        viewModel.removeSelectedImage(idToRemove)

        val params = viewModel.uiParams.value
        assertEquals(2, params.selectedImages.size)
        assertEquals(uri1, params.selectedImages[0].uri)
        assertEquals(uri3, params.selectedImages[1].uri)
        assertEquals(0, params.selectedImages[0].order)
        assertEquals(1, params.selectedImages[1].order)
    }

    @Test
    fun `test removing primary image designates next remaining image as primary`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addSelectedImages(listOf(uri1, uri2))
        val primaryId = viewModel.uiParams.value.selectedImages[0].id
        viewModel.removeSelectedImage(primaryId)

        val params = viewModel.uiParams.value
        assertEquals(1, params.selectedImages.size)
        assertEquals(uri2, params.selectedImages[0].uri)
        assertTrue(params.selectedImages[0].isPrimary)
    }

    @Test
    fun `test tapping image sets it as primary`() = runTest {
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")

        viewModel.addSelectedImages(listOf(uri1, uri2))
        val secondImageId = viewModel.uiParams.value.selectedImages[1].id

        viewModel.setPrimaryImage(secondImageId)

        val params = viewModel.uiParams.value
        assertFalse(params.selectedImages[0].isPrimary)
        assertTrue(params.selectedImages[1].isPrimary)
        assertEquals(uri2, params.sourceImageUri)
    }

    @Test
    fun `test multiple images guard prevents img2img execution`() = runTest {
        viewModel.setMode(GenerationMode.IMAGE_TO_IMAGE)
        val uri1 = Uri.parse("content://media/external/images/media/1")
        val uri2 = Uri.parse("content://media/external/images/media/2")
        viewModel.addSelectedImages(listOf(uri1, uri2))

        viewModel.onGenerateClicked()

        // Verify toast message emitted
        val toast = viewModel.toastEvents.first()
        assertEquals("Multiple images selected. Composition mode is not implemented yet.", toast)
    }
}
