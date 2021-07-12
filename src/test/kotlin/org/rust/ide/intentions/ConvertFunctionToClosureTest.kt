/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class ConvertFunctionToClosureTest : RsIntentionTestBase(ConvertFunctionToClosureIntention::class) {

    fun `test conversion from function to closure`() = doAvailableTest("""
        fn main() {
            fn foo/*caret*/(x: i32) -> i32 { x + 1 }
        }
    """, """
        fn main() {
            let foo = |x: i32| -> i32 { x + 1 };/*caret*/
        }
    """)

    fun `test intention is only available in function signature`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>fn foo(x: i32) -> i32 </selection>{ x + 1 }
        }
    """)

}
