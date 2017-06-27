/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsExtraSemicolonInspectionTest : RsInspectionsTestBase(RsExtraSemicolonInspection()) {

    fun testNotApplicableWithoutReturnType() = checkByText("""
        fn foo() { 92; }
    """)

    fun testNotApplicableForLet() = checkByText("""
        fn foo() -> i32 { let x = 92; }
    """)

    fun testNotApplicableWithExplicitReturn() = checkByText("""
        fn foo() -> i32 { return 92; }
    """)

    fun testNotApplicableWithExplicitUnitType() = checkByText("""
        fn fun() -> () { 2 + 2; }
    """)

    fun testNotApplicableWithMacro() = checkByText("""
        fn fun() -> i32 { panic!("diverge"); }
    """)

    fun testNotApplicableWithTrailingFn() = checkByText("""
        fn foo() -> bool {
            loop {}
            fn f() {}
        }
    """)

    fun testFix() = checkFixByText("Remove semicolon", """
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
}

