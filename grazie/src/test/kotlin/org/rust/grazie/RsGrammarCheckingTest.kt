/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.ide.language.LanguageGrammarChecking
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.inspections.RsInspectionsTestBase

class RsGrammarCheckingTest : RsInspectionsTestBase(GrazieInspection::class) {

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
        RsAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass), baseFileName = "lib.rs")

    override fun setUp() {
        super.setUp()
        val strategy = LanguageGrammarChecking.getStrategies().first { it is RsGrammarCheckingStrategy }
        val currentState = GrazieConfig.get()
        if (strategy.getID() !in currentState.enabledGrammarStrategies || currentState.enabledLanguages != enabledLanguages) {
            updateSettings { state ->
                state.copy(
                    enabledGrammarStrategies = state.enabledGrammarStrategies + strategy.getID(),
                    enabledLanguages = enabledLanguages
                )
            }
        }
        Disposer.register(testRootDisposable) {
            updateSettings { currentState }
        }
    }

    fun `test check literals`() = doTest("""
        fn foo() {
            let literal = "<TYPO>There is two apples</TYPO>";
            let raw_literal = r"<TYPO>There is two apples</TYPO>";
            let binary_literal = b"<TYPO>There is two apples</TYPO>";
        }
    """, checkInStringLiterals = true)

    fun `test check comments`() = doTest("""
        fn foo() {
            // <TYPO>There is two apples</TYPO>
            /* <TYPO>There is two apples</TYPO> */
            let literal = 123;
        }
    """, checkInComments = true)

    // https://github.com/intellij-rust/intellij-rust/issues/7024
    fun `test check single sentence in sequential comments 1`() = doTest("""
        fn main() {
            // Path to directory where someone <TYPO>write</TYPO>
            // and from where someone reads
            let path1 = "/foo/bar";
            /* Path to directory where someone <TYPO>write</TYPO> */
            /* and from where someone reads */
            let path2 = "/foo/bar";
        }
    """, checkInComments = true)

    // https://github.com/intellij-rust/intellij-rust/issues/7024
    fun `test check single sentence in sequential comments 2`() = doTest("""
        fn main() {
            // Path to directory where someone writes

            // <TYPE>and</TYPE> from where someone reads
            let path = "/foo/bar";
        }
    """, checkInComments = true)

    fun `test check doc comments`() = doTest("""
        /// <TYPO>There is two apples</TYPO>
        mod foo {
            //! <TYPO>There is two apples</TYPO>

            /** <TYPO>There is two apples</TYPO> */
            fn bar() {}
        }
    """, checkInDocumentation = true)

    fun `test check injected Rust code in doc comments`() = doTest("""
        ///
        /// ```
        /// let literal = "<TYPO>There is two apples</TYPO>";
        /// for i in 1..10 {}
        /// ```
        pub fn foo() {}
    """, checkInStringLiterals = true)

    fun `test no typos in injected Rust code in doc comments`() = doTest("""
        ///
        /// ```
        /// foo!(There is two apples);
        /// ```
        pub fn foo() {}
    """, checkInDocumentation = true)

    private fun doTest(
        @Language("Rust") text: String,
        checkInStringLiterals: Boolean = false,
        checkInComments: Boolean = false,
        checkInDocumentation: Boolean = false
    ) {
        updateSettings { state ->
            val newContext = state.checkingContext.copy(
                isCheckInStringLiteralsEnabled = checkInStringLiterals,
                isCheckInCommentsEnabled = checkInComments,
                isCheckInDocumentationEnabled = checkInDocumentation
            )
            state.copy(checkingContext = newContext)
        }
        checkByText(text)

        updateSettings { state ->
            val newContext = state.checkingContext.copy(
                isCheckInStringLiteralsEnabled = false,
                isCheckInCommentsEnabled = false,
                isCheckInDocumentationEnabled = false
            )
            state.copy(checkingContext = newContext)
        }

        checkByText(text.replace("<TYPO.*?>(.*?)</TYPO>".toRegex(), "$1"))
    }

    private fun updateSettings(change: (GrazieConfig.State) -> GrazieConfig.State) {
        GrazieConfig.update(change)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    companion object {
        private val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    }
}
