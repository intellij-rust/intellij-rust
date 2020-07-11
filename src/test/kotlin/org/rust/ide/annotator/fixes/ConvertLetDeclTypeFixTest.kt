/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeCheckInspection

class ConvertLetDeclTypeFixTest : RsInspectionsTestBase(RsTypeCheckInspection::class) {
    fun `test unavailable on variable write`() = checkFixIsUnavailable("Change type of", """
        fn main() {
            let mut  a = 5;
            a = <error>/*caret*/"asd"</error>;
        }
    """)

    fun `test unavailable on variable without type`() = checkFixIsUnavailable("Change type of", """
        fn main() {
            let a = /*caret*/"asd";
        }
    """)

    fun `test unavailable on complex pattern`() = checkFixIsUnavailable("Change type of", """
        fn main() {
            let (a, b) = /*caret*/"asd";
        }
    """)

    fun `test unavailable on type that cannot be used for decl`() = checkFixIsUnavailable("Change type of", """
        trait T {}

        fn foo(x: &impl T) {
            let y: i32 = <error>/*caret*/x</error>;
        }
    """)

    fun `test simple type`() = checkFixByText("Change type of `a` to `&str`", """
        fn main() {
            let a: u32 = <error>/*caret*/"asd"</error>;
        }
    """, """
        fn main() {
            let a: &str = "asd";
        }
    """)

    fun `test ref binding`() = checkFixByText("Change type of `a` to `&str`", """
        fn main() {
            let ref a: u32 = <error>/*caret*/"asd"</error>;
        }
    """, """
        fn main() {
            let ref a: &str = "asd";
        }
    """)

    fun `test mut binding`() = checkFixByText("Change type of `a` to `&str`", """
        fn main() {
            let mut a: u32 = <error>/*caret*/"asd"</error>;
        }
    """, """
        fn main() {
            let mut a: &str = "asd";
        }
    """)

    fun `test aliased type`() = checkFixByText("Change type of `a` to `T`", """
        struct S;
        type T = S;

        fn bar() -> T { unimplemented!(); }
        fn foo() {
            let a: u32 = <error>/*caret*/bar()</error>;
        }
    """, """
        struct S;
        type T = S;

        fn bar() -> T { unimplemented!(); }
        fn foo() {
            let a: T = bar();
        }
    """)

    fun `test numeric type`() = checkFixByText("Change type of `a` to `u64`", """
        fn foo() {
            let a: u32 = <error>/*caret*/0u64</error>;
        }
    """, """
        fn foo() {
            let a: u64 = 0u64;
        }
    """)
}
