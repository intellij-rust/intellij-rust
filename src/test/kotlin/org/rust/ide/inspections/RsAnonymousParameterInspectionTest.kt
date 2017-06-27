/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsAnonymousParameterInspectionTest : RsInspectionsTestBase(RsAnonymousParameterInspection()) {
    fun `test fix`() = checkFixByText("Add dummy parameter name", """
        trait Display {
            fn fmt(&self, <warning descr="Anonymous functions parameters are deprecated (RFC 1685)">F<caret></warning>);
        }
    """, """
        trait Display {
            fn fmt(&self, _: F);
        }
    """)

    fun `test no warning for fn type`() = checkByText("""
        fn foo<F: Fn(i32, &mut String)>(f: F) {}
        pub trait T {
            fn foo<F: FnMut(i32)>() {}
        }
    """)

    fun `test no warning for fn trait object`() = checkByText("""
        pub trait Registry {
            fn query(&mut self,
                     dep: &Dependency,
                     f: &mut FnMut(Summary)) -> CargoResult<()>;
        }
    """)
}
