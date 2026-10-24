package io.github.spectordev9.opencode.feature.workspace

import io.github.spectordev9.opencode.core.workspace.EntryKind
import io.github.spectordev9.opencode.data.DemoWorkspaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {
    private val viewModelScope = CoroutineScope(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        viewModelScope.cancel()
    }

    private fun workspace() = DemoWorkspaceService(
        initialFiles = mapOf(
            "a.txt" to "hello".toByteArray(),
            "bin.dat" to byteArrayOf(0, 1, 2),
            "big.txt" to ByteArray(MAX_PREVIEW_BYTES + 10) { 'a'.code.toByte() },
            "dir/nested.txt" to "nested".toByteArray(),
        ),
    )

    @Test
    fun `lists the root with directories first`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.path)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals(EntryKind.DIRECTORY, viewModel.state.value.visibleEntries.first().kind)
        assertTrue(viewModel.state.value.visibleEntries.any { it.name == "a.txt" })
        assertNull(viewModel.state.value.parentPath)
    }

    @Test
    fun `navigates directories and filters entries`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()

        viewModel.openDirectory("dir")
        advanceUntilIdle()

        assertEquals("dir", viewModel.state.value.path)
        assertEquals("", viewModel.state.value.parentPath)
        assertEquals(listOf("nested.txt"), viewModel.state.value.entries.map { it.name })

        viewModel.onQueryChange("other")
        assertTrue(viewModel.state.value.visibleEntries.isEmpty())

        viewModel.onQueryChange("nest")
        assertEquals(listOf("nested.txt"), viewModel.state.value.visibleEntries.map { it.name })
    }

    @Test
    fun `previews text files`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()
        val file = viewModel.state.value.entries.first { it.name == "a.txt" }

        viewModel.openPreview(file)
        advanceUntilIdle()

        val preview = viewModel.state.value.preview
        assertEquals("a.txt", preview?.name)
        assertEquals("hello", preview?.text)
        assertFalse(preview?.isBinary == true)
        assertFalse(preview?.isTruncated == true)
        assertNull(preview?.error)
    }

    @Test
    fun `detects binary files and truncates large previews`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()
        val binary = viewModel.state.value.entries.first { it.name == "bin.dat" }
        val large = viewModel.state.value.entries.first { it.name == "big.txt" }

        viewModel.openPreview(binary)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.preview?.isBinary == true)
        assertNull(viewModel.state.value.preview?.text)

        viewModel.openPreview(large)
        advanceUntilIdle()
        val preview = viewModel.state.value.preview
        assertFalse(preview?.isBinary == true)
        assertEquals(MAX_PREVIEW_BYTES, preview?.text?.length)
        assertTrue(preview?.isTruncated == true)
    }

    @Test
    fun `directories do not open a preview`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()
        val directory = viewModel.state.value.entries.first { it.kind == EntryKind.DIRECTORY }

        viewModel.openPreview(directory)
        advanceUntilIdle()

        assertNull(viewModel.state.value.preview)
    }

    @Test
    fun `reports a denied directory`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()

        viewModel.openDirectory("missing")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.entries.isEmpty())
        assertTrue(viewModel.state.value.error.orEmpty().contains("outside"))
    }

    @Test
    fun `closing a preview clears it`() = runTest {
        val viewModel = WorkspaceViewModel(workspace(), viewModelScope)
        advanceUntilIdle()
        val file = viewModel.state.value.entries.first { it.name == "a.txt" }
        viewModel.openPreview(file)
        advanceUntilIdle()

        viewModel.closePreview()

        assertNull(viewModel.state.value.preview)
    }
}
