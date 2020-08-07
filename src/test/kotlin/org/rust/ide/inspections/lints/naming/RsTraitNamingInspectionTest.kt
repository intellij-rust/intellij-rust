/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsTraitNamingInspection

class RsTraitNamingInspectionTest : RsInspectionsTestBase(RsTraitNamingInspection::class) {
    fun `test traits`() = checkByText("""
        trait TraitOk {}
        trait <warning descr="Trait `trait_foo` should have a camel case name such as `TraitFoo`">trait_foo</warning> {}
    """)

    fun `test traits suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        trait trait_foo {}
    """)

    fun `test traits suppression nonstandard style`() = checkByText("""
        #[allow(nonstandard_style)]
        trait trait_foo {}
    """)

    fun `test traits fix`() = checkFixByText("Rename to `HotFix`", """
        trait <warning descr="Trait `hot_fix` should have a camel case name such as `HotFix`">ho<caret>t_fix</warning> {}
        struct Patch {}
        impl hot_fix for Patch {}
    """, """
        trait HotFix {}
        struct Patch {}
        impl HotFix for Patch {}
    """)
}
