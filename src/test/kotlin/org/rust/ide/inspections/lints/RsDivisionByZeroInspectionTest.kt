/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsDivisionByZeroInspectionTest : RsInspectionsTestBase(RsDivisionByZeroInspection::class) {

    fun `test division by zero`() = checkByText("""
        fn main() {
            (<error descr="Attempt to divide by zero">1 / 0</error>) + (<error descr="Attempt to divide by zero">2 / 0</error>);
        }
    """)

    fun `test division by zero in const value evaluation E0080`() = checkByText("""
        static C: u8 = <error descr="Could not evaluate static initializer [E0080]">128 / 0</error>;

        #[repr(u8)]
        pub enum E {
            V = <error descr="Evaluation of constant value failed [E0080]">0xF0 / 0</error>
        }

        fn foo<const N: i16>() {}

        fn main() {
            let a: [i8; <error descr="Evaluation of constant value failed [E0080]">1 / 0</error>];
            let b = [1; <error descr="Evaluation of constant value failed [E0080]">1 / 0</error>];

            foo::<{ <error descr="Evaluation of constant value failed [E0080]">256 / 0</error> }>();
        }
    """)

    fun `test division by zero in const item initializer`() = checkByText("""
        const C: u8 = <error descr="Any use of this value will cause an error">1 / 0</error>;
    """)

    fun `test allow arithmetic overflow`() = checkByText("""
        #![allow(unconditional_panic)]

        fn main() {
            let _: u16 = 1 / 0;
        }
    """)

    fun `test allow const err`() = checkByText("""
        #![allow(const_err)]

        const C: u8 = 1 / 0;
    """)

    fun `test do not load ast`() = checkByFileTree("""
    //- main.rs
        mod foo;

        /*caret*/const C1: u8 = <error descr="Any use of this value will cause an error">1 / 0</error> + foo::C2;
    //- foo.rs
        pub const C2: u8 = 1 / 0;
    """)
}
