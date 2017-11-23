/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.testFramework.fixtures.CompletionAutoPopupTester

class RsCompletionAutoPopupTest : RsCompletionTestBase() {
    private lateinit var tester: CompletionAutoPopupTester

    override fun setUp() {
        super.setUp()
        tester = CompletionAutoPopupTester(myFixture)
    }

    override fun invokeTestRunnable(runnable: Runnable) {
        tester.runWithAutoPopupEnabled(runnable)
    }

    override fun runInDispatchThread(): Boolean = false


    fun `test path auto popup`() {
        val tester = CompletionAutoPopupTester(myFixture)
        myFixture.configureByText("main.rs", """
            enum Foo { Bar, Baz}
            fn main() {
                let _ = Foo<caret>
            }
        """)
        tester.typeWithPauses("::")
        check(tester.lookup != null)
    }

}

