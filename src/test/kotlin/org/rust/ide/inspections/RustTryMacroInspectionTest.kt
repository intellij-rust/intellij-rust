package org.rust.ide.inspections

class RustTryMacroInspectionTest : RustInspectionsTestBase() {

    fun testFix() = checkFixByText<RustTryMacroInspection>("Change try! to ?", """
        fn foo() -> Result<(), ()> {
            <weak_warning descr="try! macro can be replaced with ? operator">try<caret>!</weak_warning>(Err(()));
            Ok(())
        }
    """, """
        fn foo() -> Result<(), ()> {
            Err(())?;
            Ok(())
        }
    """)
}
