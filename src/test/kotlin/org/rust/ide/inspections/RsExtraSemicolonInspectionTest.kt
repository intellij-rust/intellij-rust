/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsExtraSemicolonInspectionTest : RsInspectionsTestBase(RsExtraSemicolonInspection()) {

    fun `test not applicable without return type`() = checkByText("""
        fn foo() { 92; }
    """)

    fun `test not applicable for let`() = checkByText("""
        fn foo() -> i32 { let x = 92; }
    """)

    fun `test not applicable with explicit return`() = checkByText("""
        fn foo() -> i32 { return 92; }
    """)

    fun `test not applicable with explicit unit type`() = checkByText("""
        fn fun() -> () { 2 + 2; }
    """)

    fun `test not applicable with macro`() = checkByText("""
        fn fun() -> i32 { panic!("diverge"); }
    """)

    fun `test not applicable with trailing fn`() = checkByText("""
        fn foo() -> bool {
            loop {}
            fn f() {}
        }
    """)

    fun `test not applicable with diverging if`() = checkByText("""
        fn a() -> i32 {
            if true { return 0; } else { return 6; };
        }
    """)

    fun `test fix`() = checkFixByText("Remove semicolon", """
        fn foo() -> i32 {
            let x = 92;
            <warning descr="Function returns () instead of i32">x;<caret></warning>
        }
    """, """
        fn foo() -> i32 {
            let x = 92;
            x
        }
    """)

    fun `test recurse into complex expressions`() = checkFixByText("Remove semicolon", """
        fn foo() -> i32 {
            let x = 92;
            if true {
                <warning descr="Function returns () instead of i32">x;<caret></warning>
            } else {
                x
            }
        }
    """, """
        fn foo() -> i32 {
            let x = 92;
            if true {
                x<caret>
            } else {
                x
            }
        }
    """)
}

