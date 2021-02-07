/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class ConvertClosureToFunctionTest : RsIntentionTestBase(ConvertClosureToFunctionIntention::class) {

    fun `test conversion from closure to function`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test conversion auto-generates function name for wildcard binding`() = doAvailableTest("""
        fn main() {
            let _ = |x: i32/*caret*/| -> i32 { x + 1 };
        }
    """, """
        fn main() {
            fn func/*caret*/(x: i32) -> i32 { x + 1 }
        }
    """)

    fun `test intention is only available on variable name and argument list`() = checkAvailableInSelectionOnly("""
        fn main() {
            <selection>let foo = |x: i32| -> i32 </selection>{ x + 1 };
        }
    """)

    fun `test intention adds function body when closure doesnt have one`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| -> i32 x + 1;
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test intention adds return type to function when closure doesnt have one`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| x + 1;
        }
    """, """
        fn main() {
            fn foo(x: i32) -> i32 { x + 1 }/*caret*/
        }
    """)

    fun `test intention does not add unit return type explicitly`() = doAvailableTest("""
        fn main() {
            let foo = |x: i32/*caret*/| println!(x);
        }
    """, """
        fn main() {
            fn foo(x: i32) { println!(x) }/*caret*/
        }
    """)

}
