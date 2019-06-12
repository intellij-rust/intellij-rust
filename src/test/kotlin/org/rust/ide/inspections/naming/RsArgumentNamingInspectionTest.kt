/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsArgumentNamingInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsArgumentNamingInspectionTest: RsInspectionsTestBase(RsArgumentNamingInspection::class) {
    fun `test function arguments`() = checkByText("""
        fn fn_par(
            par_ok: u32,
            <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32) {
        }
    """)

    fun `test function arguments suppression`() = checkByText("""
        #[allow(non_snake_case)]
        fn fn_par(ParFoo: u32) {}
    """)

    fun `test function arguments fix`() = checkFixByText("Rename to `arg_baz`", """
        fn test (<warning descr="Argument `Arg__Baz_` should have a snake case name such as `arg_baz`">Arg__<caret>Baz_</warning>: u32) {
            println!("{}", Arg__Baz_);
        }
    """, """
        fn test (arg_baz: u32) {
            println!("{}", arg_baz);
        }
    """)

    fun `test method arguments`() = checkByText("""
        struct Foo {}
        impl Foo {
            fn fn_par(
                par_ok: u32,
                <warning descr="Argument `ParFoo` should have a snake case name such as `par_foo`">ParFoo</warning>: u32,) {
            }
        }
    """)

    fun `test method arguments suppression`() = checkByText("""
        #![allow(non_snake_case)]
        struct Foo {}
        impl Foo {
            fn fn_par(ParFoo: u32,) {}
        }
    """)

    fun `test method arguments fix`() = checkFixByText("Rename to `m_arg`", """
        struct Foo;
        impl Foo {
            fn print(&self, <warning descr="Argument `mArg` should have a snake case name such as `m_arg`">m<caret>Arg</warning>: u32) {
                println!("{}", mArg);
            }
        }
    """, """
        struct Foo;
        impl Foo {
            fn print(&self, m_arg: u32) {
                println!("{}", m_arg);
            }
        }
    """)
}
