/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.builtin

import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.openapi.project.Project
import org.rust.lang.core.macros.*
import org.rust.lang.core.parser.RustParser
import org.rust.lang.core.parser.createAdaptedRustPsiBuilder
import org.rust.lang.core.psi.RS_LITERALS
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.openapiext.childrenWithLeaves
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import kotlin.math.max

/**
 * A macro expander for built-in macros like `concat!()` and `stringify!()`
 */
class BuiltinMacroExpander(val project: Project) : MacroExpander<RsBuiltinMacroData, DeclMacroExpansionError>() {
    override fun expandMacroAsTextWithErr(
        def: RsBuiltinMacroData,
        call: RsMacroCallData
    ): RsResult<Pair<CharSequence, RangeMap>, out DeclMacroExpansionError> {
        val macroCallBodyText = call.macroBody ?: return Err(DeclMacroExpansionError.DefSyntax)

        return when (def.name) {
            "concat" -> expandConcat(macroCallBodyText)
            else -> Err(DeclMacroExpansionError.DefSyntax)
        }
    }

    private fun expandConcat(
        macroCallBodyText: String
    ): RsResult<Pair<CharSequence, RangeMap>, out DeclMacroExpansionError> {
        val exprList = project.createAdaptedRustPsiBuilder(macroCallBodyText)
            .parseCommaSeparatedExprList()

        if (exprList.childrenWithLeaves.any { it.elementType == MACRO_EXPR }) {
            // TODO support nested macros
            return Err(DeclMacroExpansionError.DefSyntax)
        }

        val value = exprList.childrenWithLeaves
            .mapNotNull { it.findChildByType(RS_LITERALS) }
            .mapNotNull { RsLiteralKind.fromAstNode(it) }
            .joinToString(separator = "") { lit ->
                when (lit) {
                    is RsLiteralKind.Boolean -> lit.value.toString()
                    is RsLiteralKind.Char -> lit.value ?: ""
                    is RsLiteralKind.String -> lit.value ?: ""
                    is RsLiteralKind.Integer -> lit.value.toString() ?: ""
                    is RsLiteralKind.Float -> lit.offsets.value?.substring(lit.node.text)
                        ?.filter { c -> c != '_' }
                        ?: ""
                }
            }
        return Ok(wrapIntoStringLiteral(value) to RangeMap.EMPTY)
    }

    private fun PsiBuilder.parseCommaSeparatedExprList(): ASTNode {
        val m = GeneratedParserUtilBase.enter_section_(this, 0, 0, null)
        while (tokenType != null) {
            val exprParsed = RustParser.LitExpr(this, 0) || RustParser.MacroExpr(this, 0)
            if (!exprParsed) break
            GeneratedParserUtilBase.consumeTokenFast(this, COMMA) // ignore missed comma
        }
        GeneratedParserUtilBase.exit_section_(this, 0, m, EXPR, true, true, GeneratedParserUtilBase.TRUE_CONDITION)

        return treeBuilt
    }

    companion object {
        const val EXPANDER_VERSION = 0
    }
}

private fun wrapIntoStringLiteral(value: String): String {
    var maxHashes = 0
    for (i in value.indices) {
        if (value[i] == '"') {
            var hashes = 1
            for (j in (i + 1) until value.length) {
                if (value[j] == '#') {
                    hashes++
                } else {
                    break
                }
            }
            maxHashes = max(maxHashes, hashes)
        }
    }

    return if (maxHashes == 0) {
        "\"$value\""
    } else {
        val h = "#".repeat(maxHashes)
        "r$h\"$value\"$h"
    }
}
