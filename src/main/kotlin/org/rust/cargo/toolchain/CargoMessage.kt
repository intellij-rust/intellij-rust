package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class CargoTopMessage(
    val message: CargoMessage,
    val package_id: String,
    val reason: String,
    val target: Target
) {
    companion object {
        fun fromJson(json: JsonObject): CargoTopMessage? {
            if (json.getAsJsonPrimitive("reason").asString != "compiler-message") {
                return null
            }

            return Gson().fromJson(json, CargoTopMessage::class.java)
        }
    }
}

data class CargoMessage(
    val children: List<CargoMessage>,
    val code: Code?,
    val level: String,
    val message: String,
    val rendered: String?,
    val spans: List<CargoSpan>
)

data class Code(
    val code: String,
    val explanation: String?)
{
    fun formatAsLink() : String {
        val rustErrorIndexPrefix = "https://doc.rust-lang.org/error-index.html"

        return "<a href=\"$rustErrorIndexPrefix#$code\">$code</a>"
    }
}

data class CargoSpan(
    val byte_end: Int,
    val byte_start: Int,
    val column_end: Int,
    val column_start: Int,
    val expansion: Expansion?,
    val file_name: String,
    val is_primary: Boolean,
    val label: String?,
    val line_end: Int,
    val line_start: Int,
    val suggested_replacement: JsonElement,
    val text: List<CargoText>
)

data class Expansion(
    val def_site_span: CargoSpan?,
    val macro_decl_name: String,
    val span: CargoSpan
)

data class CargoText(
    val highlight_end: Int,
    val highlight_start: Int,
    val text: String?
)

data class Target(
    val crate_types: List<String>,
    val kind: List<String>,
    val name: String,
    val src_path: String
)
