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
class IntroduceLocalVariableIntentionTest : RsIntentionTestBase(IntroduceLocalVariableIntention::class) {
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

    fun `test tail exp`() = doAvailableTest("""
        fn foo() -> i32 {
            0/*caret*/
        }
    """, """
        fn foo() -> i32 {
            let /*caret*/i = 0;
            i
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

    fun `test match arm expr`() = doAvailableTest("""
        fn func() -> i32 {
            match f {
                true => 1/*caret*/,
                false => 0,
            }
        }
    """, """
        fn func() -> i32 {
            let i = 1;
            match f {
                true => i,
                false => 0,
            }
        }
    """)

    fun `test match arm return expr`() = doAvailableTest("""
        fn func() -> i32 {
            match f {
                true => return 1/*caret*/,
                false => 0,
            }
        }
    """, """
        fn func() -> i32 {
            let i = 1;
            match f {
                true => return i,
                false => 0,
            }
        }
    """)

    fun `test lambda expr`() = doAvailableTest("""
        fn func() -> i32 {
            let l = || 1/*caret*/;
        }
    """, """
        fn func() -> i32 {
            let l = || {
                let i = 1;
                i
            };
        }
    """)
}
