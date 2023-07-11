/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.grazie

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.ide.inspection.grammar.GrazieInspection
import com.intellij.grazie.jlanguage.Lang
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import org.intellij.lang.annotations.Language
import org.junit.runner.RunWith
import org.rust.RsJUnit4TestRunner
import org.rust.ide.annotator.RsAnnotationTestFixture
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.lang.RsLanguage

@RunWith(RsJUnit4TestRunner::class)
class RsGrammarCheckingTest : RsInspectionsTestBase(GrazieInspection::class) {

    override fun createAnnotationFixture(): RsAnnotationTestFixture<Unit> =
        RsAnnotationTestFixture(this, myFixture, inspectionClasses = listOf(inspectionClass), baseFileName = "lib.rs")

    override fun setUp() {
        super.setUp()
        val currentState = GrazieConfig.get()

        updateSettings { state ->
            val checkingContext = state.checkingContext.copy(
                isCheckInStringLiteralsEnabled = true,
                isCheckInCommentsEnabled = true,
                isCheckInDocumentationEnabled = true,
                enabledLanguages = setOf(RsLanguage.id),
            )
            state.copy(
                enabledLanguages = enabledLanguages,
                userEnabledRules = setOf("LanguageTool.EN.UPPERCASE_SENTENCE_START"),
                checkingContext = checkingContext
            )
        }

        Disposer.register(testRootDisposable) {
            updateSettings { currentState }
        }
    }

    fun `test check literals`() = doTest("""
        fn foo() {
            let literal = "It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human";
            let raw_literal = r"It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human";
            let binary_literal = b"It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human";
        }
    """, checkInStringLiterals = true)

    // https://github.com/intellij-rust/intellij-rust/issues/7446
    fun `test sentence capitalization in comments`() = doTest("""
        fn main() {
            // <GRAMMAR_ERROR>hello</GRAMMAR_ERROR> world. <GRAMMAR_ERROR>how</GRAMMAR_ERROR> are you Harry Potter?
            println!("Hello!")
        }
    """, checkInComments = true)

    fun `test check comments`() = doTest("""
        fn foo() {
            // It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human
            /* It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human */
            let literal = 123;
        }
    """, checkInComments = true)

    // https://github.com/intellij-rust/intellij-rust/issues/7024
    fun `test check single sentence in sequential comments 1`() = doTest("""
        fn main() {
            // With <GRAMMAR_ERROR>you</GRAMMAR_ERROR>
            // I can finally be happy
            let path1 = "/foo/bar";
            /* With <GRAMMAR_ERROR>you</GRAMMAR_ERROR> */
            /* I can finally be happy */
            let path2 = "/foo/bar";
        }
    """, checkInComments = true)

    // https://github.com/intellij-rust/intellij-rust/issues/7024
    fun `test check single sentence in sequential comments 2`() = doTest("""
        fn main() {
            // With you

            // I can finally be happy
            let path = "/foo/bar";
        }
    """, checkInComments = true)

    fun `test check doc comments`() = doTest("""
        /// It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human
        mod foo {
            //! It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human

            /** It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human */
            fn bar() {}
        }
    """, checkInDocumentation = true)

    fun `test check injected Rust code in doc comments`() = doTest("""
        ///
        /// ```
        /// let literal = "It is <GRAMMAR_ERROR>an</GRAMMAR_ERROR> friend of human";
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

        checkByText(text.replace("<GRAMMAR_ERROR.*?>(.*?)</GRAMMAR_ERROR>".toRegex(), "$1"))
    }

    private fun updateSettings(change: (GrazieConfig.State) -> GrazieConfig.State) {
        GrazieConfig.update(change)
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
    }

    companion object {
        private val enabledLanguages = setOf(Lang.AMERICAN_ENGLISH)
    }
}
