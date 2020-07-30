/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.PlatformTestUtil
import org.rust.IgnoreInPlatform
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.inspections.RsInspectionsTestBase

class RsGrammarCheckingTest : RsInspectionsTestBase(GrazieInspection::class) {

    override fun createAnnotationFixture(): RsAnnotationTestFixture =
        RsAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass), baseFileName = "lib.rs")

    override fun setUp() {
        super.setUp()
        val strategy = LanguageGrammarChecking.getStrategies().first { it is RsGrammarCheckingStrategy }
        val currentState = GrazieConfig.get()
        if (strategy.getID() !in currentState.enabledGrammarStrategies || currentState.enabledLanguages != enabledLanguages) {
            GrazieConfig.update { state ->
                state.copy(
                    enabledGrammarStrategies = state.enabledGrammarStrategies + strategy.getID(),
                    enabledLanguages = enabledLanguages
                )
            }
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        }
    }

    fun `test check literals`() = checkByText("""
        fn foo() {
            let literal = "<TYPO>There is two apples</TYPO>";
            let raw_literal = r"<TYPO>There is two apples</TYPO>";
            let binary_literal = b"<TYPO>There is two apples</TYPO>";
        }
    """)

    // https://github.com/intellij-rust/intellij-rust/issues/5530
    @IgnoreInPlatform(201)
    fun `test check doc comments`() = expect<AssertionError> {
        checkByText("""
            ///
            /// ```
            /// let literal = "<TYPO>There is two apples</TYPO>";
            /// for i in 1..10 {}
            /// ```
            pub fn foo() {}
        """)
    }

    companion object {
        private val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    }
}
