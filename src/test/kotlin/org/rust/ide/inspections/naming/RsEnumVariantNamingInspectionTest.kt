/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsEnumVariantNamingInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsEnumVariantNamingInspectionTest : RsInspectionsTestBase(RsEnumVariantNamingInspection::class) {
    fun `test enum variants`() = checkByText("""
        enum EnumVars {
            VariantOk,
            <warning descr="Enum variant `variant_foo` should have a camel case name such as `VariantFoo`">variant_foo</warning>
        }
    """)

    fun `test enum variants suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        enum EnumVars {
            variant_foo
        }
    """)

    fun `test enum variants fix`() = checkFixByText("Rename to `VarBar`", """
        enum ToFix {
            <warning descr="Enum variant `var_bar` should have a camel case name such as `VarBar`">var_b<caret>ar</warning>
        }
        fn enum_var_use() {
            let a = ToFix::var_bar;
        }
    """, """
        enum ToFix {
            VarBar
        }
        fn enum_var_use() {
            let a = ToFix::VarBar;
        }
    """)
}
