package org.rust.ide.inspections

class RsSimplifyPrintInspectionTest : RsInspectionsTestBase() {

    fun testFix() = checkFixByText<RsSimplifyPrintInspection>("Remove unnecessary argument", """
        fn main() {
            <weak_warning descr="println! macro invocation can be simplified">println!(""<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            println!();
        }
    """)
}
