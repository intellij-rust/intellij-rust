/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapiext.Testmark
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import junit.framework.ComparisonFailure
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase
import org.rust.fileTreeFromText
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.expandAllMacrosRecursively
import org.rust.lang.core.psi.ext.startOffset

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
                    "${i + 1} macro comparision failed",
                    mark
                )
            }
    }

    fun checkSingleMacro(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        InlineFile(code)
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparision failed")
    }

    fun checkSingleMacroByTree(@Language("Rust") code: String, @Language("Rust") expectedExpansion: String) {
        fileTreeFromText(code).createAndOpenFileWithCaretMarker()
        val call = findElementInEditor<RsMacroCall>("^")
        checkMacroExpansion(call, expectedExpansion, "Macro comparision failed")
    }

    protected fun checkMacroExpansion(
        macroCall: RsMacroCall,
        expectedExpansion: String,
        errorMessage: String,
        mark: Testmark? = null
    ) {
        val expand = { macroCall.expandAllMacrosRecursively(replaceDollarCrate = false) }
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
}
