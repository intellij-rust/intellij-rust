package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonObject

data class CargoMessage(
    val level: String,
    val message: String,
    val spans: List<CargoSpan>,
    val target: Target,
    val code: String
) {
    companion object {
        fun fromJson(json: JsonObject): CargoMessage? {
            val json = json.getAsJsonObject("message")
            if (json.getAsJsonPrimitive("reason").asString != "compiler-artifact") {
                return null
            }

            return Gson().fromJson(json, CargoMessage::class.java)
        }
    }
}

data class CargoSpan(
    val file_name: String,
    val column_end: Int,
    val column_start: Int,
    val line_end: Int,
    val line_start: Int,
    val is_primary: Boolean,
    val label: String
)

data class Target(
    val kind: List<String>,
    val name: String,
    val src_path: String
)
