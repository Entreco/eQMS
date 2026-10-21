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
                output = success("init", """{"path":"a project"}"""),
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
                output = success("verify", """{"path":"/tmp/project","journal_records":1}"""),
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
                output = failure("verify", "OPERATION_FAILED", "workspace is invalid"),
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
            success("init", """{"path":"/tmp/project"}""").replace("\"schema_version\":2", "\"schema_version\":\"2\"") to
                "qms returned an unsupported JSON schema version.",
            success("init", """{"path":"/tmp/project"}""").replace("\"schema_version\":2", "\"schema_version\":1") to
                "qms returned an unsupported JSON schema version.",
            failure("init", "INVALID_ARGUMENT", "invalid") to
                "qms returned an unsuccessful response.",
            success("verify", """{"path":"/tmp/project"}""") to
                "qms returned a response for an unexpected command.",
            success("init", "{}") to
                "qms returned a response without a project path.",
            success("init", """{"path":42}""") to
                "qms returned a response without a project path.",
            success("init", """{"path":"/tmp/project"}""").replace("\"schema_version\":2", "\"schema_version\":{}") to
                "qms returned an unsupported JSON schema version.",
            success("init", """{"path":"/tmp/project"}""").replace("\"schema_version\":2", "\"schema_version\":\"2\"") to
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

    private fun success(command: String, data: String) =
        """{"schema_version":2,"status":"ok","command":"$command","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":$data,"error":null}"""

    private fun failure(command: String, code: String, message: String) =
        """{"schema_version":2,"status":"error","command":"$command","timestamp_unix_ms":1792600000000,"findings":[],"references":[],"data":null,"error":{"code":"$code","message":"$message"}}"""

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
