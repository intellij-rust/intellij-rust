/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeAliasNamingInspection

class RsTypeAliasNamingInspectionTest : RsInspectionsTestBase(RsTypeAliasNamingInspection()) {
    fun `test type aliases`() = checkByText("""
        type TypeOk = u32;
        type <warning descr="Type `type_foo` should have a camel case name such as `TypeFoo`">type_foo</warning> = u32;
    """)

    fun `test type aliases suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        type type_foo = u32;
    """)

    fun `test type aliases fix`() = checkFixByText("Rename to `ULong`", """
        type <warning descr="Type `u_long` should have a camel case name such as `ULong`">u_<caret>long</warning> = u64;
        const ZERO: u_long = 0;
    """, """
        type ULong = u64;
        const ZERO: ULong = 0;
    """)
}
