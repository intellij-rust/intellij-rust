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
import org.rust.lang.core.macros.decl.DeclMacroExpander
import org.rust.lang.core.macros.errors.DeclMacroExpansionError
import org.rust.lang.core.macros.errors.MacroExpansionAndParsingError
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.resolveToMacroWithoutPsi
import org.rust.openapiext.Testmark
import org.rust.stdext.RsResult
import org.rust.stdext.unwrapOrElse
import kotlin.math.min

abstract class RsMacroExpansionTestBase : RsTestBase() {
    fun doTest(@Language("Rust") code: String, @Language("Rust") vararg expectedExpansions: Pair<String, Testmark?>) {
        InlineFile(code)
        checkAllMacroExpansionsInFile(myFixture.file, expectedExpansions)
    }

    fun doTest(
        mark: Testmark,
        @Language("Rust") code: String,
        @Language("Rust") vararg expectedExpansions: String
    ) {
        mark.checkHit { doTest(code, *expectedExpansions) }
    }

    fun doTest(
        @Language("Rust") code: String,
        @Language("Rust") vararg expectedExpansions: String
    ) {
        doTest(code, *expectedExpansions.map { Pair<String, Testmark?>(it, null) }.toTypedArray())
    }

    protected fun checkAllMacroExpansionsInFile(file: PsiFile, expectedExpansions: Array<out Pair<String, Testmark?>>) {
        val calls = file.descendantsOfType<RsMacroCall>()
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

    fun checkSingleMacro(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        InlineFile(code)
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparison failed")
    }

    fun checkSingleMacroByTree(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparison failed")
    }

    fun doErrorTest(@Language("Rust") code: String, mark: Testmark) {
        InlineFile(code)
        mark.checkHit {
            val call = findElementInEditor<RsMacroCall>("^")
            val def = call.resolveToMacro() ?: error("Failed to resolve macro ${call.path.text}")
            check(expandMacroAsTextWithErr(call, RsDeclMacroData(def)).isErr)
        }
    }

    private fun checkMacroExpansion(
        macroCall: RsMacroCall,
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

    private fun expandMacroOrFail(call: RsPossibleMacroCall): MacroExpansion {
        require(call is RsMacroCall)
        call.resolveToMacroWithoutPsi()
        val def = call.resolveToMacroWithoutPsi().ok()?.data as? RsDeclMacroData
            ?: error("Failed to resolve macro `${call.path.text}`")
        return expandMacroAsTextWithErr(call, def).unwrapOrElse { err ->
            val description = err.formatError(call)
            error("Failed to expand macro `${call.path.text}`: $description")
        }
    }

    private fun expandMacroAsTextWithErr(
        call: RsMacroCall,
        def: RsDeclMacroData
    ): RsResult<MacroExpansion, MacroExpansionAndParsingError<DeclMacroExpansionError>> {
        val expander = DeclMacroExpander(project)
        val expansionResult = expander.expandMacro(
            RsMacroDataWithHash(def, null),
            call,
            RsPsiFactory(project, markGenerated = false),
            storeRangeMap = true,
            useCache = false
        )
        return expansionResult.map {
            it.elements.forEach { el ->
                el.setContext(call.context as RsElement)
                el.setExpandedFrom(call)
            }
            it
        }
    }

    private fun MacroExpansionAndParsingError<DeclMacroExpansionError>.formatError(call: RsMacroCall) = when (this) {
        is MacroExpansionAndParsingError.ExpansionError -> error.formatError(call)
        is MacroExpansionAndParsingError.ParsingError -> "can't parse expansion text `$expansionText` as $context"
        MacroExpansionAndParsingError.MacroCallSyntaxError -> "there is a syntax error in the macro call"
    }

    private fun DeclMacroExpansionError.formatError(call: RsMacroCall) = when (this) {
        is DeclMacroExpansionError.Matching -> {
            val macroBody = call.macroBody ?: ""
            errors.withIndex().joinToString(prefix = "no matching patterns:\n", separator = "\n") { (i, e) ->
                val tail = macroBody.substring(e.offsetInCallBody, min(e.offsetInCallBody + 10, macroBody.length))
                "\t#${i + 1} $e at `$tail`"
            }
        }
        DeclMacroExpansionError.DefSyntax -> "syntax error in the macro definition"
    }
}
