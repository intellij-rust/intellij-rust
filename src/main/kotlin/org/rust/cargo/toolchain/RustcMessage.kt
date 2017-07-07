/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject

data class CargoTopMessage(
    val message: RustcMessage,
    val package_id: String,
    val reason: String,
    val target: Target
) {
    companion object {
        fun fromJson(json: JsonObject): CargoTopMessage? {
            if (json.getAsJsonPrimitive("reason")?.asString != "compiler-message") {
                return null
            }

            return Gson().fromJson(json, CargoTopMessage::class.java)
                ?: error("Failed to parse CargoTopMessage from $json")
        }
    }
}

data class RustcMessage(
    val children: List<RustcMessage>,
    val code: ErrorCode?,
    val level: String,
    val message: String,
    val rendered: String?,
    val spans: List<RustcSpan>
)

data class ErrorCode(
    val code: String,
    val explanation: String?
)

data class RustcSpan(
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
    val text: List<RustcText>
)

data class Expansion(
    val def_site_span: RustcSpan?,
    val macro_decl_name: String,
    val span: RustcSpan
)

data class RustcText(
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
