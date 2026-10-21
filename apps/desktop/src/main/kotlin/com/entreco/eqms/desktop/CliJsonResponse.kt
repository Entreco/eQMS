package com.entreco.eqms.desktop

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

internal fun JsonObject.qmsSchemaVersion(): Int? =
    (this["schema_version"] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.intOrNull

internal fun JsonObject.isQmsJsonV2Envelope(): Boolean {
    val status = stringValue("status")
    val command = this["command"]
    val timestamp = (this["timestamp_unix_ms"] as? JsonPrimitive)
        ?.takeUnless(JsonPrimitive::isString)
        ?.longOrNull
    if (qmsSchemaVersion() != 2 || (status != "ok" && status != "error") ||
        (command !== JsonNull && (command as? JsonPrimitive)?.isString != true) ||
        timestamp == null || this["findings"] !is JsonArray ||
        this["references"] !is JsonArray || !containsKey("data") ||
        !containsKey("error")
    ) {
        return false
    }
    return when (status) {
        "ok" -> this["error"] === JsonNull
        "error" -> {
            val error = this["error"] as? JsonObject
            val code = error?.stringValue("code")
            val message = error?.stringValue("message")
            this["data"] === JsonNull && !code.isNullOrBlank() && !message.isNullOrBlank()
        }
        else -> false
    }
}

internal fun JsonObject.stringValue(name: String): String? =
    (this[name] as? JsonPrimitive)
        ?.takeIf(JsonPrimitive::isString)
        ?.content

internal fun JsonObject.errorMessage(): String? =
    (this["error"] as? JsonObject)
        ?.stringValue("message")
        ?.takeIf(String::isNotBlank)
