package com.entreco.eqms.desktop

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectDirectoryChooserTest {
    @Test
    fun createsNormalAndSpacedChildFolders() = withTemporaryDirectory { parent ->
        for (name in listOf("project", "project with spaces")) {
            val folder = createProjectFolder(parent, name)
            assertEquals(parent.resolve(name), folder)
            assertTrue(Files.isDirectory(folder))
        }
    }

    @Test
    fun rejectsNamesThatAreNotSingleChildFolders() = withTemporaryDirectory { parent ->
        for (name in listOf("", " ", ".", "..", "/absolute", "a/b", "a\\b", "C:project", "bad\u0000name")) {
            assertFailsWith<IllegalArgumentException>(name) {
                createProjectFolder(parent, name)
            }
        }
        Files.list(parent).use { assertEquals(0L, it.count()) }
    }

    @Test
    fun refusesExistingDirectoriesAndFilesWithoutChangingContents() = withTemporaryDirectory { parent ->
        val existing = Files.createDirectory(parent.resolve("existing"))
        val content = Files.writeString(existing.resolve("keep.txt"), "unchanged")
        val file = Files.writeString(parent.resolve("file"), "also unchanged")
        for (name in listOf("existing", "file")) {
            assertFailsWith<FileAlreadyExistsException> {
                createProjectFolder(parent, name)
            }
        }
        assertEquals("unchanged", Files.readString(content))
        assertEquals("also unchanged", Files.readString(file))
    }

    @Test
    fun propagatesFilesystemFailureInsteadOfReturningSuccess() = withTemporaryDirectory { parent ->
        assertFailsWith<NoSuchFileException> {
            createProjectFolder(parent.resolve("missing"), "project")
        }
    }

    @Test
    fun installsExplicitNewFolderActionOnlyForCreate() {
        SwingUtilities.invokeAndWait {
            val create = projectDirectoryChooser(WorkspaceOperation.Create)
            val panel = create.accessory as JPanel
            assertEquals("New folder", (panel.getComponent(0) as JButton).text)
            assertNull(projectDirectoryChooser(WorkspaceOperation.Open).accessory)
        }
    }

    private fun withTemporaryDirectory(test: (java.nio.file.Path) -> Unit) {
        val directory = Files.createTempDirectory("eqms-folder-test-")
        try {
            test(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
