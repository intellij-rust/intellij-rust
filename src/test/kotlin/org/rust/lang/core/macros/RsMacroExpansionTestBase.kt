/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import junit.framework.ComparisonFailure
import org.apache.commons.lang3.StringUtils
import org.intellij.lang.annotations.Language
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.expansion
import org.rust.openapiext.Testmark

abstract class RsMacroExpansionTestBase : RsTestBase() {
    fun doTest(@Language("Rust") code: String, @Language("Rust") vararg expectedExpansions: Pair<String, Testmark?>) {
        InlineFile(code)
        val calls = myFixture.file.descendantsOfType<RsMacroCall>()
        check(calls.size == expectedExpansions.size) {
            "Number of macros calls is not equals to number of expected expansions: " +
                "${calls.size} != ${expectedExpansions.size}"
        }
        calls
            .zip(expectedExpansions)
            .forEachIndexed { i, (macroCall, expectedExpansionAndMark) ->
                val (expectedExpansion, mark) = expectedExpansionAndMark
                val expansion = run {
                    @Suppress("IfThenToElvis") // `macroCall.expansion` is nullable
                    if (mark != null) {
                        mark.checkHit { macroCall.expansion }
                    } else {
                        macroCall.expansion
                    }
                } ?: error("Macro expansion failed `${macroCall.text}`")

                val expandedText = expansion.joinToString("\n") { it.text }

                if (StringUtils.deleteWhitespace(expandedText) != StringUtils.deleteWhitespace(expectedExpansion)) {
                    throw ComparisonFailure(
                        "${i + 1} macro comparision failed",
                        expectedExpansion,
                        expandedText
                    )
                }
            }
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
}
