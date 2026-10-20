package com.entreco.eqms.desktop

import java.io.IOException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WorkspaceCliTest {
    @Test
    fun createPassesJsonCommandAndPathAsSeparateArguments() {
        val path = Path.of("a project")
        val executor = RecordingExecutor(
            ProcessResult(
                exitCode = 0,
                output = """{"schema_version":1,"status":"ok","command":"init","path":"a project"}""",
            ),
        )

        val result = WorkspaceCli("qms-test", executor).execute(WorkspaceOperation.Create, path)

        assertEquals(listOf("init", "--json", "a project"), executor.arguments)
        assertEquals("qms-test", executor.executable)
        assertEquals(WorkspaceCommandResult.Success("a project"), result)
    }

    @Test
    fun openAcceptsOnlySuccessfulVerifyResponse() {
        val executor = RecordingExecutor(
            ProcessResult(
                exitCode = 0,
                output = """{"schema_version":1,"status":"ok","command":"verify","path":"/tmp/project","journal_records":1}""",
            ),
        )

        val result = WorkspaceCli("qms-test", executor)
            .execute(WorkspaceOperation.Open, Path.of("/tmp/project"))

        assertEquals(listOf("verify", "--json", "/tmp/project"), executor.arguments)
        assertEquals(WorkspaceCommandResult.Success("/tmp/project"), result)
    }

    @Test
    fun nonzeroExitSurfacesStructuredCliError() {
        val executor = RecordingExecutor(
            ProcessResult(
                exitCode = 1,
                output = """{"schema_version":1,"status":"error","error":{"code":"OPERATION_FAILED","message":"workspace is invalid"}}""",
            ),
        )

        val result = WorkspaceCli("qms-test", executor)
            .execute(WorkspaceOperation.Open, Path.of("/tmp/invalid"))

        assertEquals(WorkspaceCommandResult.Failure("workspace is invalid"), result)
    }

    @Test
    fun malformedUnsupportedAndMismatchedResponsesAreRejected() {
        val outputs = listOf(
            "not json" to "qms returned an invalid JSON response.",
            """{"schema_version":2,"status":"ok","command":"init","path":"/tmp/project"}""" to
                "qms returned an unsupported JSON schema version.",
            """{"schema_version":1,"status":"error","command":"init","path":"/tmp/project"}""" to
                "qms returned an unsuccessful response.",
            """{"schema_version":1,"status":"ok","command":"verify","path":"/tmp/project"}""" to
                "qms returned a response for an unexpected command.",
            """{"schema_version":1,"status":"ok","command":"init"}""" to
                "qms returned a response without a project path.",
            """{"schema_version":1,"status":"ok","command":"init","path":42}""" to
                "qms returned a response without a project path.",
            """{"schema_version":{},"status":"ok","command":"init","path":"/tmp/project"}""" to
                "qms returned an unsupported JSON schema version.",
            """{"schema_version":"1","status":"ok","command":"init","path":"/tmp/project"}""" to
                "qms returned an unsupported JSON schema version.",
        )

        outputs.forEach { (output, expectedMessage) ->
            val result = WorkspaceCli(
                "qms-test",
                RecordingExecutor(ProcessResult(0, output)),
            ).execute(WorkspaceOperation.Create, Path.of("/tmp/project"))
            assertEquals(WorkspaceCommandResult.Failure(expectedMessage), result)
        }
    }

    @Test
    fun executableLaunchFailureIsReported() {
        val result = WorkspaceCli(
            "missing-qms",
            ProcessExecutor { _, _ -> throw IOException("not found") },
        ).execute(WorkspaceOperation.Create, Path.of("/tmp/project"))

        val failure = assertIs<WorkspaceCommandResult.Failure>(result)
        assertEquals(
            "Could not start qms. Set EQMS_QMS_PATH to the native executable: not found",
            failure.message,
        )
    }
}

private class RecordingExecutor(
    private val result: ProcessResult,
) : ProcessExecutor {
    var executable: String? = null
        private set
    var arguments: List<String>? = null
        private set

    override fun execute(executable: String, arguments: List<String>): ProcessResult {
        this.executable = executable
        this.arguments = arguments
        return result
    }
}
