/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable
import org.intellij.lang.annotations.Language

class RsCompletionAutoPopupTest : RsCompletionTestBase() {
    private lateinit var tester: CompletionAutoPopupTester

    fun `test path`() = checkPopupIsShownAfterTyping("""
        enum Foo { Bar, Baz}
        fn main() {
            let _ = Foo/*caret*/
        }
    """, "::")

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

    private fun configureAndType(code: String, toType: String) {
        InlineFile(code).withCaret()
        tester.typeWithPauses(toType)
    }
}
