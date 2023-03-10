/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsTryMacroInspectionTest : RsInspectionsTestBase(RsTryMacroInspection::class) {

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test fix`() = checkFixByText("Change try! to ?", """
        fn foo() -> Result<(), ()> {
            <weak_warning descr="try! macro can be replaced with ? operator">try/*caret*/!(Err(()));</weak_warning>
            Ok(())
        }
    """, """
        fn foo() -> Result<(), ()> {
            Err(())?;
            Ok(())
        }
    """, checkWeakWarn = true)
}
