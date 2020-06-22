/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

/**
 * [IntroduceLocalVariableIntention] is based on
 * [org.rust.ide.refactoring.introduceVariable.RsIntroduceVariableHandler], so most of tests from
 * [org.rust.ide.refactoring.RsIntroduceVariableHandlerTest] are also applicable.
 */
class IntroduceLocalVariableIntentionTest : RsIntentionTestBase(IntroduceLocalVariableIntention()) {
    fun `test unavailable if there is a variable`() = doUnavailableTest("""
        fn main() {
            let a = /*caret*/foo();
        }
        fn foo() -> i32 { 0 }
    """)

    fun `test unavailable if expression type is '()'`() = doUnavailableTest("""
        fn main() {
            /*caret*/foo();
        }
        fn foo() {}
    """)

    fun `test available if expression type not '()'`() = doAvailableTest("""
        fn main() {
            /*caret*/foo();
        }
        fn foo() -> i32 { 0 }
    """, """
        fn main() {
            let /*caret*/i = foo();
        }
        fn foo() -> i32 { 0 }
    """)

    fun `test simple`() = doAvailableTest("""
        fn main() {
            /*caret*/0;
        }
    """, """
        fn main() {
            let /*caret*/i = 0;
        }
    """)

    fun `test composite`() = doAvailableTest("""
        fn main() {
            2 + /*caret*/2 + 2;
        }
    """, """
        fn main() {
            let /*caret*/x = 2 + 2 + 2;
        }
    """)

    fun `test caret after expression`() = doAvailableTest("""
        fn main() {
            0/*caret*/;
        }
    """, """
        fn main() {
            let /*caret*/i = 0;
        }
    """)

    fun `test no semicolon`() = doAvailableTest("""
        fn main() {
            0/*caret*/
            let foo = 1;
        }
    """, """
        fn main() {
            let /*caret*/i = 0;
            let foo = 1;
        }
    """)

    // TODO really should put `i` as a tail expr (leaving semantics the same), looks like a bug in the refactoring
    fun `test tail exp`() = doAvailableTest("""
        fn foo() -> i32 {
            0/*caret*/
        }
    """, """
        fn foo() -> i32 {
            let /*caret*/i = 0;
        }
    """)

    fun `test return exp`() = doAvailableTest("""
        fn foo() -> i32 {
            return 0/*caret*/;
        }
    """, """
        fn foo() -> i32 {
            let /*caret*/i = 0;
            return i;
        }
    """)
}
