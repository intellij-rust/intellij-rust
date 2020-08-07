/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.lints.RsLifetimeNamingInspection

class RsLifetimeNamingInspectionTest : RsInspectionsTestBase(RsLifetimeNamingInspection::class) {
    fun `test lifetimes`() = checkByText("""
        fn lifetimes<
            'lifetime_ok,
            '__,
            <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'LifetimeFoo</warning>>() {
        }
    """)

    fun `test lifetimes suppression`() = checkByText("""
        #[allow(non_snake_case)]
        fn lifetimes<'LifetimeFoo>() {}
    """)

    fun `test lifetimes fix`() = checkFixByText("Rename to `'lifetime_foo`", """
        fn lifetimes<
            <warning descr="Lifetime `'LifetimeFoo` should have a snake case name such as `'lifetime_foo`">'Lifetime<caret>Foo</warning>>(x: &'LifetimeFoo u32) {
        }
    """, """
        fn lifetimes<
            'lifetime_foo>(x: &'lifetime_foo u32) {
        }
    """)
}
