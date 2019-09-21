/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.intellij.lang.annotations.Language

abstract class CargoTomlCompletionTestBase : BasePlatformTestCase() {
    protected fun doSingleCompletion(@Language("TOML") before: String, @Language("TOML") after: String) {
        checkByText(before, after) { executeSoloCompletion() }
    }

    private fun executeSoloCompletion() {
        val variants = myFixture.completeBasic()

        if (variants != null) {
            if (variants.size == 1) {
                // for cases like `frob/*caret*/nicate()`,
                // completion won't be selected automatically.
                myFixture.type('\n')
                return
            }

            fun LookupElement.debug(): String = "$lookupString ($psiElement)"

            error("Expected a single completion, but got ${variants.size}\n"
                + variants.joinToString("\n") { it.debug() })
        }
    }

    protected fun checkByText(
        before: String,
        after: String,
        action: () -> Unit
    ) {
        myFixture.configureByText("Cargo.toml", before)
        action()
        myFixture.checkResult(after)
    }

    protected fun checkNoCompletion(@Language("TOML") code: String) {
        myFixture.configureByText("Cargo.toml", code)
        noCompletionCheck()
    }

    private fun noCompletionCheck() {
        val variants = myFixture.completeBasic()
        checkNotNull(variants) {
            val element = myFixture.file.findElementAt(myFixture.caretOffset - 1)
            "Expected zero completions, but one completion was auto inserted: `${element?.text}`."
        }
        check(variants.isEmpty()) {
            "Expected zero completions, got ${variants.size}: ${variants.map { it.lookupString }}."
        }
    }
}
