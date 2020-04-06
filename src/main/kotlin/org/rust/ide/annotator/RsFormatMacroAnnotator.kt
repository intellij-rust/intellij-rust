/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapiext.isUnitTestMode
import com.intellij.psi.PsiElement
import org.intellij.lang.annotations.Language
import org.rust.ide.colors.RsColor
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.descendantOfTypeStrict
import org.rust.lang.core.psi.ext.macroName
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.psi.kind
import org.rust.lang.utils.parseRustStringCharacters

class RsFormatMacroAnnotator : AnnotatorBase() {

    override fun annotateInternal(element: PsiElement, holder: AnnotationHolder) {
        val formatMacro = element as? RsMacroCall ?: return

        val formatStr = formatMacro
            .formatMacroArgument
            ?.formatMacroArgList
            ?.getOrNull(macroToFormatPos(formatMacro.macroName))
            ?.descendantOfTypeStrict<RsLitExpr>()
            ?: return

        val literalKind = (formatStr.kind as? RsLiteralKind.String) ?: return
        val rawTextRange = literalKind.offsets.value ?: return
        val (unescapedText, sourceMap, _) = parseRustStringCharacters(rawTextRange.substring(literalKind.node.text))

        val arguments = formatParser.findAll(unescapedText)
        var currentOffset = 0

        fun IntRange.toSourceRange(additionalOffset: Int = currentOffset): TextRange =
            TextRange(sourceMap[first + additionalOffset], sourceMap[last + additionalOffset] + 1)
                .shiftRight(formatStr.startOffset + rawTextRange.startOffset)

        fun AnnotationHolder.highlightArgument(range: IntRange?, color: RsColor = RsColor.IDENTIFIER) {
            if (range != null && !range.isEmpty()) {
                // BACKCOMPAT: 2019.3
                @Suppress("DEPRECATION")
                createAnnotation(
                    HighlightSeverity.INFORMATION,
                    range.toSourceRange(),
                    null
                ).textAttributes = color.textAttributesKey
            }
        }

        val key = RsColor.FORMAT_SPECIFIER
        val highlightSeverity = if (isUnitTestMode) key.testSeverity else HighlightSeverity.INFORMATION
        for (arg in arguments) {
            currentOffset = 0
            val textRange = arg.range.toSourceRange()
            // BACKCOMPAT: 2019.3
            @Suppress("DEPRECATION")
            holder.createAnnotation(highlightSeverity, textRange, null)
                .textAttributes = key.textAttributesKey

            if (arg.groups[3] != null) {
                // BACKCOMPAT: 2019.3
                @Suppress("DEPRECATION")
                holder.createErrorAnnotation(
                    arg.range.toSourceRange(),
                    "Invalid format string: unmatched '}'"
                )
            }
            if (arg.groups[1] != null) {

                val inside = arg.groups[2] ?: error(" should not be null because can match empty string")
                currentOffset = inside.range.first
                val matchResult = formatArgParser.find(inside.value)
                    ?: error(" should be not null because can match empty string")

                if (!arg.groups[1]!!.value.endsWith("}")) {
                    val possibleEnd = matchResult.value.length - 1
                    // BACKCOMPAT: 2019.3
                    @Suppress("DEPRECATION")
                    holder.createErrorAnnotation(
                        (possibleEnd..possibleEnd).toSourceRange(),
                        "Invalid format string: } expected.\nIf you intended to print `{` symbol, you can escape it using `{{`"
                    )
                    continue
                }

                val validParsedEnd = matchResult.range.last + 1
                if (validParsedEnd != inside.value.length) {
                    // BACKCOMPAT: 2019.3
                    @Suppress("DEPRECATION")
                    holder.createErrorAnnotation(
                        (validParsedEnd until inside.value.length).toSourceRange(),
                        "Invalid format string"
                    )
                } else {
                    holder.highlightArgument(matchResult.groups["id"]?.range)
                    holder.highlightArgument(matchResult.groups["width"]?.range)
                    holder.highlightArgument(matchResult.groups["precision"]?.range)
                    holder.highlightArgument(matchResult.groups["type"]?.range, RsColor.FUNCTION)
                    //TODO: argument type/count/other checks
                }
            }
        }
    }

    private fun macroToFormatPos(macro: String): Int = when (macro) {
        "println" -> 0
        "print" -> 0
        "eprintln" -> 0
        "eprint" -> 0
        "format" -> 0
        "format_args" -> 0
        "format_args_nl" -> 0
        "write" -> 1
        "writeln" -> 1
        else -> -1
    }

    companion object {
        private val formatParser = Regex("""\{\{|}}|(\{([^}]*)}?)|(})""")

        @Language("Regexp")
        private const val argument = """([a-zA-Z_][\w+]*|\d+)"""
        private val formatArgParser = Regex("""(?x) # enable comments
^(?<id>$argument)?
(:
    (.?[\^<>])?[+\-]?\#?
    0?(?!\$) # negative lookahead to parse 0$ as width and 00$ as zero padding followed by width
    (?<width>$argument\$|\d+)?
    (\.(?<precision>$argument\$|\d+|\*))?
    (?<type>\w?\??)?
)?\s*""")

    }
}
