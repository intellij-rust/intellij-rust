/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester
import com.intellij.util.ThrowableRunnable

class RsCompletionAutoPopupTest : RsCompletionTestBase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        tester.runWithAutoPopupEnabled(testRunnable)
    }

    override fun runInDispatchThread(): Boolean = false

    fun `test path auto popup`() {
        myFixture.configureByText("main.rs", """
            enum Foo { Bar, Baz}
            fn main() {
                let _ = Foo<caret>
            }
        """)
        tester.typeWithPauses("::")

        assertNotNull(tester.lookup)
    }
}
