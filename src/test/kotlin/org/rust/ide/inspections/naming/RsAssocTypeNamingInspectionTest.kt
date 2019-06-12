/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsAssocTypeNamingInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsAssocTypeNamingInspectionTest : RsInspectionsTestBase(RsAssocTypeNamingInspection::class) {
    fun `test associated types`() = checkByText("""
        trait Foo {
            type AssocTypeOk;
            type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">assoc_foo</warning>;
        }
    """)

    fun `test associated types suppression`() = checkByText("""
        #[allow(non_camel_case_types)]
        trait Foo {
            type assoc_foo;
        }
    """)

    fun `test associated types fix`() = checkFixByText("Rename to `AssocFoo`", """
        trait Foo {
            type <warning descr="Type `assoc_foo` should have a camel case name such as `AssocFoo`">ass<caret>oc_foo</warning>;
            fn bar(foo: &Self::assoc_foo) {}
        }
    """, """
        trait Foo {
            type AssocFoo;
            fn bar(foo: &Self::AssocFoo) {}
        }
    """)
}
