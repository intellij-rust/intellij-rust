/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsMacroNamingInspection

class RsMacroNamingInspectionTest : RsInspectionsTestBase(RsMacroNamingInspection::class) {
    fun `test macros`() = checkByText("""
        macro_rules! macro_ok { () => {}; }
        macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">MacroFoo</warning> { () => {}; }
    """)

    fun `test macros suppression`() = checkByText("""
        #[allow(non_snake_case)]
        macro_rules! MacroFoo { () => {}; }
    """)

    fun `test macros fix`() = checkFixByText("Rename to `macro_foo`", """
        macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">Macro<caret>Foo</warning> { () => {}; }
        MacroFoo!();
    """, """
        macro_rules! macro_foo { () => {}; }
        macro_foo!();
    """)

    fun `test macros with raw identifier`() = checkFixByText("Rename to `macro_foo`", """
        macro_rules! <warning descr="Macro `MacroFoo` should have a snake case name such as `macro_foo`">r#Macro/*caret*/Foo</warning> { () => {}; }
        r#MacroFoo!();
    """, """
        macro_rules! macro_foo { () => {}; }
        macro_foo!();
    """)
}
