/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class RsCompletionLiteralSuffixAutoPopupTest(private val toType: String) : RsCompletionTestBase() {
    private lateinit var tester: CompletionAutoPopupTester

    @Test
    fun `test popup is shown when typing literal suffix`() = checkPopupIsShownAfterTyping("""
        fn main() {
            let a = 5/*caret*/
        }
    """, toType)


    @Test
    fun `test popup is shown when typing literal suffix for a negative number`() = checkPopupIsShownAfterTyping("""
        fn main() {
            let a = -5/*caret*/
        }
    """, toType)

    @Test
    fun `test popup is not shown when typing variable name`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a/*caret*/
        }
    """, toType)

    @Test
    fun `test popup is not shown when referencing a variable`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = non_existing_/*caret*/
        }
    """, toType)

    @Test
    fun `test popup is not shown when typing in string literal`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = "b/*caret*/"
        }
    """, toType)

    @Test
    fun `test popup is not shown when typing in not closed string literal`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = "b/*caret*/
        }
    """, toType)

    @Test
    fun `test popup is not shown when typing in char literal`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = 'b/*caret*/'
        }
    """, toType)

    @Test
    fun `test popup is not shown when typing in not closed char literal`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = 'b/*caret*/
        }
    """, toType)

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    override fun runInDispatchThread(): Boolean = false

    private fun checkPopupIsShownAfterTyping(@Language("Rust") code: String, toType: String) {
        configureAndType(code, toType)
        assertNotNull(tester.lookup)
    }

    private fun checkPopupIsNotShownAfterTyping(@Language("Rust") code: String, toType: String) {
        configureAndType(code, toType)
        assertNull(tester.lookup)
    }

    private fun configureAndType(code: String, toType: String) {
        InlineFile(code).withCaret()
        tester.typeWithPauses(toType)
    }


    companion object {
        @Parameterized.Parameters(name = "char: {0}")
        @JvmStatic
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("i"),
            arrayOf("u"),
            arrayOf("f"),
        )
    }
}
