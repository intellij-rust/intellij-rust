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

    fun `test lambda argument 1`() = checkPopupIsShownAfterTyping("""
        fn foo(a: fn()) {}
        fn main() {
            foo(/*caret*/);
        }
    """, "|")

    fun `test lambda argument 2`() = checkPopupIsShownAfterTyping("""
        fn foo(a: i32, b: fn()) {}
        fn main() {
            foo(0, /*caret*/);
        }
    """, "|")

    fun `test lambda argument 3`() = checkPopupIsShownAfterTyping("""
        fn foo(a: i32, b: fn()) {}
        fn main() {
            foo(
                0,
                /*caret*/
            );
        }
    """, "|")

    fun `test lambda assignment`() = checkPopupIsShownAfterTyping("""
        fn main() {
            let a: fn() = /*caret*/
        }
    """, "|")

    fun `test popup is not shown after typing bit OR operator`() = checkPopupIsNotShownAfterTyping("""
        fn main() {
            let a = 1;
            let b = a/*caret*/
        }
    """, "|")

    fun `test popup is not shown after typing OR pattern`() = checkPopupIsNotShownAfterTyping("""
        const C: i32 = 0;
        fn foo(a: Option<E>) {
            match a {
                Some(/*caret*/) => {},
                _ => {}
            }
        }
    """, "|")

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
}
