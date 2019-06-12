/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsVariableNamingInspection

class RsVariableNamingInspectionTest : RsInspectionsTestBase(RsVariableNamingInspection::class) {
    fun `test variables`() = checkByText("""
        fn loc_var() {
            let var_ok = 12;
            let <warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning> = 12;
        }
    """)

    fun `test variables suppression`() = checkByText("""
        #![allow(non_snake_case)]
        fn loc_var() {
            let VarFoo = 12;
        }
    """)

    fun `test variables within struct`() = checkByText("""
        struct Foo { fld: u32 }
        fn test() {
            let Foo { fld: <warning descr="Variable `FLD_VAL` should have a snake case name such as `fld_val`">FLD_VAL</warning> } = Foo { fld: 17 };
        }
    """)

    fun `test variables fix`() = checkFixByText("Rename to `dwarfs_count`", """
        fn test() {
            let <warning descr="Variable `DWARFS_COUNT` should have a snake case name such as `dwarfs_count`">DWARF<caret>S_COUNT</warning> = 7;
            let legs_count = DWARFS_COUNT * 2;
        }
    """, """
        fn test() {
            let dwarfs_count = 7;
            let legs_count = dwarfs_count * 2;
        }
    """)

    fun `test tuple variables`() = checkByText("""
        fn loc_var() {
            let (var1_ok, var2_ok) = (17, 83);
            let (<warning descr="Variable `VarFoo` should have a snake case name such as `var_foo`">VarFoo</warning>, var2_ok) = (120, 30);
        }
    """)

    fun `test tuple variables fix`() = checkFixByText("Rename to `real`", """
        fn test() {
            let (<warning descr="Variable `Real` should have a snake case name such as `real`">Re<caret>al</warning>, imaginary) = (7.2, 3.5);
            println!("{} + {}i", Real, imaginary);
        }
    """, """
        fn test() {
            let (real, imaginary) = (7.2, 3.5);
            println!("{} + {}i", real, imaginary);
        }
    """)

    // Issue #730. The inspection must not be applied in the following cases
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test variables not applied`() = checkByText("""
        fn test_not_applied() {
            match Some(()) {
                None => ()
            }

            match 1 {
                Foo => { }
            }

            let seven = Some(7);
            if let Some(Number) = seven {
            }

            let (a, b) = (Some(10), Some(12));
            match (a, b) {
                (None, Some(x)) => {}
                _ => {}
            }
        }
    """)
}
