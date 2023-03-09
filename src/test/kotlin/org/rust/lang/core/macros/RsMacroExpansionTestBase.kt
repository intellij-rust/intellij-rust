/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.crate.impl.FakeCrate
import org.rust.lang.core.macros.errors.*
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.openapiext.Testmark
import org.rust.stdext.RsResult
import org.rust.stdext.unwrapOrElse
import kotlin.math.min

abstract class RsMacroExpansionTestBase : RsTestBase() {
    protected fun doTest(@Language("Rust") code: String, @Language("Rust") vararg expectedExpansions: Pair<String, Testmark?>) {
        InlineFile(code)
        checkAllMacroExpansionsInFile(myFixture.file, expectedExpansions)
    }

    protected fun doTest(
        @Language("Rust") code: String,
        @Language("Rust") vararg expectedExpansions: String
    ) {
        doTest(code, *expectedExpansions.map { Pair<String, Testmark?>(it, null) }.toTypedArray())
    }

    protected fun checkAllMacroExpansionsInFile(file: PsiFile, expectedExpansions: Array<out Pair<String, Testmark?>>) {
        val calls = file.descendantsOfType<RsPossibleMacroCall>().filter { it.isMacroCall }
        check(calls.size == expectedExpansions.size) {
            "Number of macros calls is not equals to number of expected expansions: " +
                "${calls.size} != ${expectedExpansions.size}"
        }
        calls
            .zip(expectedExpansions)
            .forEachIndexed { i, (macroCall, expectedExpansionAndMark) ->
                val (expectedExpansion, mark) = expectedExpansionAndMark
                checkMacroExpansion(
                    macroCall,
                    expectedExpansion,
                    "${i + 1} macro comparison failed",
                    mark
                )
            }
    }

    protected fun checkSingleMacro(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        InlineFile(code)
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparison failed")
    }

    protected fun checkSingleMacroByTree(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparison failed")
    }

    protected fun doErrorTest(@Language("Rust") code: String) {
        InlineFile(code)
        val call = findElementInEditor<RsMacroCall>("^")
        val def = call.resolveToMacroWithoutPsi().ok() ?: error("Failed to resolve macro ${call.path.text}")
        check(expandMacroAsTextWithErr(call, def.data).isErr)
    }

    private fun checkMacroExpansion(
        macroCall: RsPossibleMacroCall,
        expectedExpansion: String,
        errorMessage: String,
        mark: Testmark? = null
    ) {
        val expand = { macroCall.expandMacrosRecursively(replaceDollarCrate = false, expander = ::expandMacroOrFail) }
        val expandedText = mark?.checkHit(expand) ?: expand()

        if (!StringUtil.equalsIgnoreWhitespaces(expectedExpansion, expandedText)) {
            val formattedExpandedText =
                parseExpandedTextWithContext(macroCall.expansionContext, RsPsiFactory(project), expandedText)
                    ?.elements.orEmpty()
                    .map {
                        CodeStyleManager.getInstance(project).reformatRange(it, it.startOffset, it.endOffset, true)
                    }.joinToString("\n") { it.text }

            throw ComparisonFailure(
                errorMessage,
                expectedExpansion.trimIndent(),
                formattedExpandedText
            )
        }
    }

    open fun expandMacroOrFail(call: RsPossibleMacroCall): MacroExpansion {
        val def = call.resolveToMacroWithoutPsiWithErr()
            .unwrapOrElse { error("Failed to resolve macro `${call.path?.text}`: $it") }
            .data
        return expandMacroAsTextWithErr(call, def).unwrapOrElse { err ->
            val description = err.formatError(call)
            error("Failed to expand macro `${call.path?.text}`: $description")
        }
    }

    private fun expandMacroAsTextWithErr(
        call: RsPossibleMacroCall,
        def: RsMacroData
    ): RsResult<MacroExpansion, MacroExpansionAndParsingError<MacroExpansionError>> {
        val crate = call.containingCrate
        check(crate !is FakeCrate) { "Invalid `containingCrate` for the macro call" }
        val expander = FunctionLikeMacroExpander.forCrate(crate)
        val expansionResult = expander.expandMacro(
            RsMacroDataWithHash(def, null),
            call,
            storeRangeMap = true,
            useCache = false
        )
        return expansionResult.map {
            it.elements.forEach { el ->
                el.setContext(call.contextToSetForExpansion as RsElement)
                el.setExpandedFrom(call)
            }
            it
        }
    }

    private fun MacroExpansionAndParsingError<MacroExpansionError>.formatError(call: RsPossibleMacroCall): String = when (this) {
        is MacroExpansionAndParsingError.ExpansionError -> error.formatError(call)
        is MacroExpansionAndParsingError.ParsingError -> "can't parse expansion text `$expansionText` as $context"
        MacroExpansionAndParsingError.MacroCallSyntaxError -> "there is a syntax error in the macro call"
    }

    private fun MacroExpansionError.formatError(call: RsPossibleMacroCall): String = when (this) {
        BuiltinMacroExpansionError -> toString()
        is DeclMacroExpansionError -> formatDeclMacroError(call as RsMacroCall)
        is ProcMacroExpansionError -> toString()
    }

    private fun DeclMacroExpansionError.formatDeclMacroError(call: RsMacroCall): String = when (this) {
        is DeclMacroExpansionError.Matching -> {
            val macroBody = call.macroBody ?: ""
            errors.withIndex().joinToString(prefix = "no matching patterns:\n", separator = "\n") { (i, e) ->
                val tail = macroBody.substring(e.offsetInCallBody, min(e.offsetInCallBody + 10, macroBody.length))
                "\t#${i + 1} $e at `$tail`"
            }
        }
        DeclMacroExpansionError.DefSyntax -> "syntax error in the macro definition"
        DeclMacroExpansionError.TooLargeExpansion -> "too large expansion"
    }
}
