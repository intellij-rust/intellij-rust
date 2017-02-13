package org.rust.ide.inspections

class RsSimplifyBooleanExpressionInspectionTest : RsInspectionsTestBase(RsSimplifyBooleanExpressionInspection()) {
    fun `test subexpression`() {
        //FIXME: throws Must not change PSI outside command or undo-transparent action.
        if (true) return
        checkByText("""
            fn main() {
                let _ = true && foo && bar;
            }
        """)
    }
}
