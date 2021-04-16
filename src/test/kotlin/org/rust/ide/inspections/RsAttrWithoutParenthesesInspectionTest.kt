/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.MockAdditionalCfgOptions

class RsAttrWithoutParenthesesInspectionTest : RsInspectionsTestBase(RsAttrWithoutParenthesesInspection::class) {

    fun `test allow without parentheses`() = checkErrors("""
        #[<error descr="Malformed `allow` attribute input: missing parentheses">allow</error>]
        fn main() {}
    """)

    fun `test derive without parentheses`() = checkErrors("""
        #[<error descr="Malformed `derive` attribute input: missing parentheses">derive</error>]
        struct S;
    """)

    // `test` does not require parentheses, so this test checks that there are no errors.
    fun `test test attribute without parentheses`() = checkErrors("""
        #[test]
        fn main() {}
    """)

    fun `test attr with known attr item`() = checkErrors("""
        #[custom_attr(repr)]
        struct Foo(i32);
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test error in cfg attr items`() = checkErrors("""
        #[cfg_attr(intellij_rust, <error descr="Malformed `repr` attribute input: missing parentheses">repr</error>)]
        struct Foo(u8);
    """)

    fun `test inner attr`() = checkErrors("""
        fn main() {
            #![<error descr="Malformed `warn` attribute input: missing parentheses">warn</error>]
            let x = 5;
        }
    """)

    // Note: this tests both the fix and it moving the caret to the parentheses.
    fun `test fix repr without parentheses`() = checkFixByText("Add parentheses to `repr`", """
        #[<error descr="Malformed `repr` attribute input: missing parentheses">re/*caret*/pr</error>]
        enum E {
            V
        }
    """, """
        #[repr(/*caret*/)]
        enum E {
            V
        }
    """)

    fun `test fix allow without parentheses`() = checkFixByText("Add parentheses to `allow`", """
        #[<error descr="Malformed `allow` attribute input: missing parentheses">al/*caret*/low</error>]
        fn main() {}
    """, """
        #[allow(/*caret*/)]
        fn main() {}
    """)

}
