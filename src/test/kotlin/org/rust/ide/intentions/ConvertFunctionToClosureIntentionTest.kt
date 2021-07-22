/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertFunctionToClosureIntentionTest : RsIntentionTestBase(ConvertFunctionToClosureIntention::class) {

    fun `test availability range`() = checkAvailableInSelectionOnly("""
        fn main() {
            #[foo]
            <selection>fn foo(x: i32) -> i32</selection> { x + 1 }
        }
    """)

    fun `test no parameters`() = doAvailableTest("""
        fn main() {
            fn foo/*caret*/() { x + 1 }
        }
    """, """
        fn main() {
            let foo = || { x + 1 };/*caret*/
        }
    """)

    fun `test one parameter`() = doAvailableTest("""
        fn main() {
            fn foo/*caret*/(x: i32) -> i32 { x + 1 }
        }
    """, """
        fn main() {
            let foo = |x: i32| -> i32 { x + 1 };/*caret*/
        }
    """)

    fun `test raw identifier`() = doAvailableTest("""
        fn main() {
            fn r#match/*caret*/(x: i32) -> i32 { x + 1 }
        }
    """, """
        fn main() {
            let r#match = |x: i32| -> i32 { x + 1 };/*caret*/
        }
    """)

    fun `test unavailable for generic function`() = doUnavailableTest("""
        fn main() {
            fn /*caret*/foo<T>(x: T) -> T { x }
        }
    """)
}
