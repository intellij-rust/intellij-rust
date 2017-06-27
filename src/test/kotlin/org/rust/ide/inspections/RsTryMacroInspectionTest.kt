/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsTryMacroInspectionTest : RsInspectionsTestBase(RsTryMacroInspection()) {

    fun testFix() = checkFixByText("Change try! to ?", """
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
