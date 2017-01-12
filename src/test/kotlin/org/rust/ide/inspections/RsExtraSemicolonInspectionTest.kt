package org.rust.ide.inspections

class RsExtraSemicolonInspectionTest : RsInspectionsTestBase() {

    fun testNotApplicableWithoutReturnType() = checkByText<RsExtraSemicolonInspection>("""
        fn foo() { 92; }
    """)

    fun testNotApplicableForLet() = checkByText<RsExtraSemicolonInspection>("""
        fn foo() -> i32 { let x = 92; }
    """)

    fun testNotApplicableWithExplicitReturn() = checkByText<RsExtraSemicolonInspection>("""
        fn foo() -> i32 { return 92; }
    """)

    fun testNotApplicableWithExplicitUnitType() = checkByText<RsExtraSemicolonInspection>("""
        fn fun() -> () { 2 + 2; }
    """)

    fun testNotApplicableWithMacro() = checkByText<RsExtraSemicolonInspection>("""
        fn fun() -> i32 { panic!("diverge"); }
    """)

    fun testNotApplicableWithTrailingFn() = checkByText<RsExtraSemicolonInspection>("""
        fn foo() -> bool {
            loop {}
            fn f() {}
        }
    """)

    fun testFix() = checkFixByText<RsExtraSemicolonInspection>("Remove semicolon", """
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

