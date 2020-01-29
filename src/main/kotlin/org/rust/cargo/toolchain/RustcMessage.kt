/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import org.rust.ide.annotator.isValid

// https://docs.rs/cargo/0.33.0/cargo/util/machine_message/struct.FromCompiler.html
data class CargoTopMessage(
    val message: RustcMessage,
    val package_id: String,
    val reason: String,
    val target: Target
) {
    companion object {

        private val LOG = Logger.getInstance(CargoTopMessage::class.java)

        fun fromJson(json: JsonObject): CargoTopMessage? {
            if (json.getAsJsonPrimitive("reason")?.asString != "compiler-message") {
                return null
            }

            val message = try {
                Gson().fromJson(json, CargoTopMessage::class.java)
            } catch (e: JsonSyntaxException) {
                LOG.warn(e)
                null
            }
            return message ?: error("Failed to parse CargoTopMessage from $json")
        }
    }
}

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/json/struct.Diagnostic.html
data class RustcMessage(
    val children: List<RustcMessage>,
    val code: ErrorCode?,
    val level: String,
    val message: String,
    val rendered: String?,
    val spans: List<RustcSpan>
) {
    val mainSpan: RustcSpan?
        get() {
            val validSpan = spans.filter { it.isValid() }.firstOrNull { it.is_primary } ?: return null
            return generateSequence(validSpan) { it.expansion?.span }.last()
                .takeIf { it.isValid() && !it.file_name.startsWith("<") }
        }
}

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/json/struct.DiagnosticCode.html
data class ErrorCode(
    val code: String,
    val explanation: String?
)

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/json/struct.DiagnosticSpan.html
data class RustcSpan(
    val file_name: String,
    val byte_start: Int,
    val byte_end: Int,
    val line_start: Int,
    val line_end: Int,
    val column_start: Int,
    val column_end: Int,
    val is_primary: Boolean,
    val text: List<RustcText>,
    val label: String?,
    val suggested_replacement: String?,
    val suggestion_applicability: Applicability?,
    val expansion: Expansion?
) {
    fun toTextRange(document: Document): TextRange? {
        val startOffset = toOffset(document, line_start, column_start)
        val endOffset = toOffset(document, line_end, column_end)
        return if (startOffset != null && endOffset != null && startOffset < endOffset) {
            TextRange(startOffset, endOffset)
        } else {
            null
        }
    }

    companion object {
        @Suppress("NAME_SHADOWING")
        fun toOffset(document: Document, line: Int, column: Int): Int? {
            val line = line - 1
            val column = column - 1
            if (line >= document.lineCount) return null
            return (document.getLineStartOffset(line) + column)
                .takeIf { it <= document.textLength }
        }
    }
}

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/json/struct.DiagnosticSpanMacroExpansion.html
data class Expansion(
    val def_site_span: RustcSpan?,
    val macro_decl_name: String,
    val span: RustcSpan
)

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/json/struct.DiagnosticSpanLine.html
data class RustcText(
    val highlight_end: Int,
    val highlight_start: Int,
    val text: String?
)

// https://docs.rs/cargo/0.33.0/cargo/core/manifest/struct.Target.html
data class Target(
    val crate_types: List<String>,
    val kind: List<String>,
    val name: String,
    val src_path: String
)

// https://doc.rust-lang.org/nightly/nightly-rustc/syntax/diagnostics/plugin/enum.Applicability.html
enum class Applicability {
    @SerializedName("MachineApplicable")
    MACHINE_APPLICABLE,
    @SerializedName("MaybeIncorrect")
    MAYBE_INCORRECT,
    @SerializedName("HasPlaceholders")
    HAS_PLACEHOLDERS,
    @SerializedName("Unspecified")
    UNSPECIFIED
}

/**
 * Represents execution result of build script
 *
 * @see <a href="https://github.com/rust-lang/cargo/blob/f0f73f04d104b67f982c3e24f074f48308c0afd0/src/cargo/util/machine_message.rs#L62-L70">machine_message.rs</a>
 */
data class BuildScriptMessage(
    val package_id: String,
    val cfgs: List<String>,
    val env: List<List<String>>,
    val out_dir: String?
) {
    companion object {
        fun fromJson(json: JsonObject): BuildScriptMessage? {
            if (json.getAsJsonPrimitive("reason")?.asString != "build-script-executed") return null
            return Gson().fromJson(json, BuildScriptMessage::class.java)
        }
    }
}
