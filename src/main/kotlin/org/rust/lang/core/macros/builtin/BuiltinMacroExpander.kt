/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.builtin

import com.intellij.openapi.project.Project
import org.rust.ide.annotator.format.*
import org.rust.lang.core.lexer.getRustLexerTokenType
import org.rust.lang.core.macros.*
import org.rust.lang.core.macros.errors.BuiltinMacroExpansionError
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import org.rust.lang.core.psi.RS_IDENTIFIER_TOKENS
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsFormatMacroArgument
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonCommentSibling
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok

/**
 * A macro expander for built-in macros like `concat!()` and `stringify!()`
 */
class BuiltinMacroExpander(val project: Project) : MacroExpander<RsBuiltinMacroData, BuiltinMacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsBuiltinMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, BuiltinMacroExpansionError> {
        val macroBody = call.macroBody
        if (def.name in BUILTIN_FORMAT_MACROS && macroBody is MacroCallBody.FunctionLike) {
            val formatMacro = parseMacroBodyAsFormat(project, macroBody) ?: return Err(BuiltinMacroExpansionError)
            val result = handleFormatMacro(def.name, macroBody.text, formatMacro) ?: return Err(BuiltinMacroExpansionError)
            return Ok(result.text to result.ranges)
        }
        return Err(BuiltinMacroExpansionError)
    }

    companion object {
        private val BUILTIN_FORMAT_MACROS: Set<String> = setOf("format_args", "format_args_nl")

        const val EXPANDER_VERSION = 3
    }
}

private fun parseMacroBodyAsFormat(project: Project, macroBody: MacroCallBody.FunctionLike): RsFormatMacroArgument? {
    val text = "(${macroBody.text})"

    val (builder, _) = project
        .createAdaptedRustPsiBuilder(text)
        .lowerDocCommentsToAdaptedPsiBuilder(project)

    val result = RustParser.FormatMacroArgument(builder, 0)
    if (!result) return null

    return builder.treeBuilt.psi as? RsFormatMacroArgument
}

private fun handleFormatMacro(macroName: String, macroText: String, format: RsFormatMacroArgument): MappedText? {
    val formatStr = format.formatMacroArgList
        .getOrNull(0)
        ?.expr as? RsLitExpr
        ?: return null

    val parseCtx = parseParameters(formatStr) ?: return null
    val errors = checkSyntaxErrors(parseCtx)
    if (errors.isNotEmpty()) return null

    val namedArguments = mutableSetOf<String>()
    for (argument in format.formatMacroArgList) {
        val name = argument.name()
        if (name != null) {
            namedArguments.add(name)
        }
    }

    val macroBuilder = MutableMappedText(macroText.length * 2)
    macroBuilder.appendUnmapped("$macroName!(")
    macroBuilder.appendMapped(macroText, 0)

    var endsWithComma = format.lastChild.getPrevNonCommentSibling()?.elementType == RsElementTypes.COMMA

    fun addImplicitArgument(name: String, offset: Int) {
        // Make sure that we have ', ' at the end before we add the new argument
        if (!endsWithComma) {
            macroBuilder.appendUnmapped(", ")
            endsWithComma = false
        }
        else if (macroBuilder.text.lastOrNull() != ' ') {
            macroBuilder.appendUnmapped(" ")
        }
        macroBuilder.appendUnmapped("$name = ")
        macroBuilder.appendMapped(name, offset)
    }

    var hasChanges = false
    val parameters = buildParameters(parseCtx)
    for (parameter in parameters) {
        if (parameter.lookup is ParameterLookup.Named) {
            val name = parameter.lookup.name
            if (name.getRustLexerTokenType() !in RS_IDENTIFIER_TOKENS) {
                return null
            }
            if (name !in namedArguments) {
                // Candidate for implicit argument
                // We subtract 1 because we have added '(' to the beginning of the format so that it could be parsed
                addImplicitArgument(name, parameter.range.startOffset - 1)
                hasChanges = true
            }
        }
    }

    if (!hasChanges) {
        return null
    }

    macroBuilder.appendUnmapped(")")
    return macroBuilder.toMappedText()
}
