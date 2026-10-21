package com.entreco.eqms.desktop

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopProjectCapabilitiesTest {
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
    fun listsAndCreatesFoldersThroughPlatformCapabilities() = withTemporaryDirectory { parent ->
        kotlinx.coroutines.runBlocking {
            val capabilities = DesktopProjectCapabilities()
            val created = capabilities.createFolder(parent.toString(), "new project").getOrThrow()
            val listing = capabilities.listDirectory(parent.toString()).getOrThrow()
            assertEquals(listOf("new project"), listing.children.map { it.name })
            assertEquals(java.nio.file.Path.of(created).toRealPath().toString(), listing.children.single().path)
            assertTrue(capabilities.createFolder(parent.toString(), "new project").isFailure)
            assertTrue(capabilities.listDirectory(parent.resolve("missing").toString()).isFailure)
        }
    }

    @Test
    fun workspaceCapabilityDelegatesToAuthoritativeCliAndReportsFailure() {
        kotlinx.coroutines.runBlocking {
            val commands = mutableListOf<List<String>>()
            val cli = WorkspaceCli("test-qms", ProcessExecutor { _, arguments ->
                commands += arguments
                if (arguments.first() == "init") {
                    ProcessResult(
                        0,
                        """{"schema_version":2,"status":"ok","command":"init","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":{"path":"/project with spaces"},"error":null}""",
                    )
                } else {
                    ProcessResult(
                        1,
                        """{"schema_version":2,"status":"error","command":"verify","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":null,"error":{"code":"OPERATION_FAILED","message":"Invalid workspace"}}""",
                    )
                }
            })
            val capabilities = DesktopProjectCapabilities(cli)
            assertEquals("/project with spaces", capabilities.openProject("/project with spaces", true).getOrThrow())
            assertEquals("Invalid workspace", capabilities.openProject("/invalid", false).exceptionOrNull()?.message)
            assertEquals(
                listOf(listOf("init", "--json", "/project with spaces"), listOf("verify", "--json", "/invalid")),
                commands,
            )
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
