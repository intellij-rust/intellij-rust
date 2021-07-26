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

    fun `test popup is not shown when typing lowercase let binding`() = checkPopupIsNotShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            let /*caret*/
        }
    """, "a")

    fun `test popup is not shown when typing nested lowercase let binding`() = checkPopupIsNotShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            let (a, /*caret*/)
        }
    """, "a")

    fun `test popup is shown when typing uppercase let binding`() = checkPopupIsShownAfterTyping("""
        struct A1 { f: i32 }
        const A2: i32 = 1;
        fn main() {
            let /*caret*/
        }
    """, "A")

    fun `test popup is shown when typing if let binding`() = checkPopupIsShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            if let /*caret*/
        }
    """, "a")

    fun `test popup is shown when typing while let binding`() = checkPopupIsShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            while let /*caret*/
        }
    """, "a")

    fun `test popup is shown when typing binding in match arm`() = checkPopupIsShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            match 1 {
                /*caret*/
            }
        }
    """, "a")

    fun `test popup is shown when typing let mut 1`() = checkPopupIsShownAfterTyping("""
        fn main() {
            let /*caret*/
        }
    """, "m")

    fun `test popup is shown when typing let mut 2`() = checkPopupIsShownAfterTyping("""
        fn main() {
            let m/*caret*/
        }
    """, "u")

    fun `test popup is not shown when typing lowercase let mut binding`() = checkPopupIsNotShownAfterTyping("""
        struct a1 { f: i32 }
        const a2: i32 = 1;
        fn main() {
            let mut /*caret*/
        }
    """, "a")

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
