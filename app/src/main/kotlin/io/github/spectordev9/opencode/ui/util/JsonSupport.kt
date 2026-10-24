package io.github.spectordev9.opencode.ui.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

fun Any?.toJsonElement(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is Map<*, *> -> JsonObject(
        entries.mapNotNull { (key, value) ->
            (key as? String)?.let { it to value.toJsonElement() }
        }.toMap(),
    )
    is Iterable<*> -> JsonArray(map { it.toJsonElement() })
    is Array<*> -> JsonArray(map { it.toJsonElement() })
    is Boolean -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is String -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

fun Map<String, Any?>.toJsonObject(): JsonObject = JsonObject(
    entries.associate { (key, value) -> key to value.toJsonElement() },
)

fun JsonObject.stringOrNull(name: String): String? =
    (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content

fun Map<String, Any?>.stringOrNull(name: String): String? =
    (this[name]?.toJsonElement() as? JsonPrimitive)?.takeIf { it.isString }?.content

fun JsonObject.longOrNull(name: String): Long? =
    (this[name] as? JsonPrimitive)?.longOrNull

fun JsonObject.doubleOrNull(name: String): Double? =
    (this[name] as? JsonPrimitive)?.doubleOrNull

fun JsonObject.booleanOrNull(name: String): Boolean? =
    (this[name] as? JsonPrimitive)?.booleanOrNull

fun JsonObject.objectOrNull(name: String): JsonObject? = this[name] as? JsonObject

fun JsonObject.longFrom(vararg names: String): Long? =
    names.firstNotNullOfOrNull { longOrNull(it) }
