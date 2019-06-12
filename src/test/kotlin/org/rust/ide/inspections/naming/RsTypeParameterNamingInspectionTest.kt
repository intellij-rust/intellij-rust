/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsTypeParameterNamingInspection

class RsTypeParameterNamingInspectionTest : RsInspectionsTestBase(RsTypeParameterNamingInspection::class) {
    fun `test type parameters`() = checkByText("""
        fn type_params<
            SomeType: Clone,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>: Clone> () {
        }
    """)

    fun `test type parameters suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        fn type_params<some_Type: Clone> () {}
    """)

    fun `test type parameters fix`() = checkFixByText("Rename to `To`", """
        fn type_params<<warning descr="Type parameter `to` should have a camel case name such as `To`">t<caret>o</warning>: Clone> () {}
    """, """
        fn type_params<To: Clone> () {}
    """)

    fun `test type parameters with where`() = checkByText("""
        fn type_params<
            SomeType,
            <warning descr="Type parameter `some_Type` should have a camel case name such as `SomeType`">some_Type</warning>>() where some_Type: Clone {
        }
    """)

    fun `test type parameters with where fix`() = checkFixByText("Rename to `Base`", """
        fn type_params<<warning descr="Type parameter `base` should have a camel case name such as `Base`">b<caret>ase</warning>>(b: &base) where base: Clone {}
    """, """
        fn type_params<Base>(b: &Base) where Base: Clone {}
    """)
}
