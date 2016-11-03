package org.rust.ide.inspections

class RustExtraSemicolonInspectionTest : RustInspectionsTestBase() {
    override val dataPath: String get() = ""

    fun testNotApplicableWithoutReturnType() = checkByText<RustExtraSemicolonInspection>(" fn foo() { 92; }")
    fun testNotApplicableForLet() = checkByText<RustExtraSemicolonInspection>(" fn foo() -> i32 { let x = 92; }")

    fun testFix() = checkFixByText<RustExtraSemicolonInspection>("Remove semicolon", """
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

