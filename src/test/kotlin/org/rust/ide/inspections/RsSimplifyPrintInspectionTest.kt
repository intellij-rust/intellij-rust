/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsSimplifyPrintInspectionTest : RsInspectionsTestBase(RsSimplifyPrintInspection()) {

    fun testFix() = checkFixByText("Remove unnecessary argument", """
        fn main() {
            <weak_warning descr="println! macro invocation can be simplified">println!(""<caret>)</weak_warning>;
        }
    """, """
        fn main() {
            println!();
        }
    """)
}
