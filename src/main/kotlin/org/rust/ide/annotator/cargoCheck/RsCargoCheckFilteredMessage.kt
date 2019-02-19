/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.cargoCheck

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.PathUtil
import org.apache.commons.lang.StringEscapeUtils
import org.rust.cargo.toolchain.ErrorCode
import org.rust.cargo.toolchain.RustcMessage
import org.rust.cargo.toolchain.RustcSpan
import org.rust.lang.RsConstants
import java.util.*

data class RsCargoCheckFilteredMessage(
    val severity: HighlightSeverity,
    val textRange: TextRange,
    val message: String,
    val htmlTooltip: String
) {
    companion object {
        fun filterMessage(file: PsiFile, document: Document, message: RustcMessage): RsCargoCheckFilteredMessage? {
            if (message.message.startsWith("aborting due to") || message.message.startsWith("cannot continue")) {
                return null
            }

            val severity = when (message.level) {
                "error" -> HighlightSeverity.ERROR
                "warning" -> HighlightSeverity.WEAK_WARNING
                else -> HighlightSeverity.INFORMATION
            }

            val span = message.spans
                .firstOrNull { it.is_primary && it.isValid() }
                // Some error messages are global, and we *could* show then atop of the editor,
                // but they look rather ugly, so just skip them.
                ?: return null

            val syntaxErrors = listOf("expected pattern", "unexpected token")
            if (syntaxErrors.any { it in span.label.orEmpty() || it in message.message }) {
                return null
            }

            val spanFilePath = PathUtil.toSystemIndependentName(span.file_name)
            if (!file.virtualFile.path.endsWith(spanFilePath)) return null

            @Suppress("NAME_SHADOWING")
            fun toOffset(line: Int, column: Int): Int? {
                val line = line - 1
                val column = column - 1
                if (line >= document.lineCount) return null
                return (document.getLineStartOffset(line) + column)
                    .takeIf { it <= document.textLength }
            }

            // The compiler message lines and columns are 1 based while intellij idea are 0 based
            val startOffset = toOffset(span.line_start, span.column_start)
            val endOffset = toOffset(span.line_end, span.column_end)
            val textRange = if (startOffset != null && endOffset != null && startOffset < endOffset) {
                TextRange(startOffset, endOffset)
            } else {
                return null
            }

            val tooltip = with(ArrayList<String>()) {
                val code = message.code.formatAsLink()
                add(StringEscapeUtils.escapeHtml(message.message) + if (code == null) "" else " $code")

                if (span.label != null && !message.message.startsWith(span.label)) {
                    add(StringEscapeUtils.escapeHtml(span.label))
                }

                message.children
                    .filter { !it.message.isBlank() }
                    .map { "${it.level.capitalize()}: ${StringEscapeUtils.escapeHtml(it.message)}" }
                    .forEach { add(it) }

                joinToString("<br>") { formatMessage(it) }
            }

            return RsCargoCheckFilteredMessage(severity, textRange, message.message, tooltip)
        }
    }
}

private fun RustcSpan.isValid(): Boolean =
    line_end > line_start || (line_end == line_start && column_end >= column_start)

private fun ErrorCode?.formatAsLink(): String? =
    if (this?.code.isNullOrBlank()) null else "<a href=\"${RsConstants.ERROR_INDEX_URL}#${this?.code}\">${this?.code}</a>"


private fun formatMessage(message: String): String {
    data class Group(val isList: Boolean, val lines: ArrayList<String>)

    val (lastGroup, groups) =
        message.split("\n").fold(
            Pair(null as Group?, ArrayList<Group>())
        ) { (group: Group?, acc: ArrayList<Group>), lineWithPrefix ->
            val (isListItem, line) = if (lineWithPrefix.startsWith("-")) {
                true to lineWithPrefix.substring(2)
            } else {
                false to lineWithPrefix
            }

            when {
                group == null -> Pair(Group(isListItem, arrayListOf(line)), acc)
                group.isList == isListItem -> {
                    group.lines.add(line)
                    Pair(group, acc)
                }
                else -> {
                    acc.add(group)
                    Pair(Group(isListItem, arrayListOf(line)), acc)
                }
            }
        }
    if (lastGroup != null && lastGroup.lines.isNotEmpty()) groups.add(lastGroup)

    return groups.joinToString {
        if (it.isList) "<ul>${it.lines.joinToString("<li>", "<li>")}</ul>"
        else it.lines.joinToString("<br>")
    }
}
