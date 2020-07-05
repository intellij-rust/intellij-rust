/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ide.inspections.RsInspectionsTestBase

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
class RsIntegerOverflowInspectionTest : RsInspectionsTestBase(RsIntegerOverflowInspection::class) {
    fun `test literal out of range`() = checkByText("""
        fn main() {
            let _: u8 = <error descr="Literal out of range for u8">256</error>;
            let _: u16 = <error descr="Literal out of range for u16">65_536</error>;
            let _: u32 = <error descr="Literal out of range for u32">4_294_967_296</error>;
            let _: u64 = <error descr="Literal out of range for u64">18_446_744_073_709_551_616</error>;
            let _: u128 = <error descr="Literal out of range for u128">340_282_366_920_938_463_463_374_607_431_768_211_456</error>;
            let _: usize = <error descr="Literal out of range for usize">18_446_744_073_709_551_616</error>;

            // Compiler produces E0600 here instead of `Literal out of range`. It should be handled by RsErrorAnnotator
            let _: u8 = -1;
            let _: u16 = -1;
            let _: u32 = -1;
            let _: u64 = -1;
            let _: u128 = -1;
            let _: usize = -1;

            let _: i8 = <error descr="Literal out of range for i8">128</error>;
            let _: i16 = <error descr="Literal out of range for i16">32_768</error>;
            let _: i32 = <error descr="Literal out of range for i32">2_147_483_648</error>;
            let _: i64 = <error descr="Literal out of range for i64">9_223_372_036_854_775_808</error>;
            let _: i128 = <error descr="Literal out of range for i128">170_141_183_460_469_231_731_687_303_715_884_105_728</error>;
            let _: isize = <error descr="Literal out of range for isize">9_223_372_036_854_775_808</error>;

            let _: i8 = <error descr="Literal out of range for i8">-129</error>;
            let _: i16 = <error descr="Literal out of range for i16">-32_769</error>;
            let _: i32 = <error descr="Literal out of range for i32">-2_147_483_649</error>;
            let _: i64 = <error descr="Literal out of range for i64">-9_223_372_036_854_775_809</error>;
            let _: i128 = <error descr="Literal out of range for i128">-170_141_183_460_469_231_731_687_303_715_884_105_729</error>;
            let _: isize = <error descr="Literal out of range for isize">-9_223_372_036_854_775_809</error>;
        }
    """)

    fun `test integer type bounds`() = checkByText("""
        fn main() {
            let _: u8 = 255;
            let _: u16 = 65_535;
            let _: u32 = 4_294_967_295;
            let _: u64 = 18_446_744_073_709_551_615;
            let _: u128 = 340_282_366_920_938_463_463_374_607_431_768_211_455;
            let _: usize = 18_446_744_073_709_551_615;

            let _: u8 = 0;
            let _: u16 = 0;
            let _: u32 = 0;
            let _: u64 = 0;
            let _: u128 = 0;
            let _: usize = 0;

            let _: i8 = 127;
            let _: i16 = 32_767;
            let _: i32 = 2_147_483_647;
            let _: i64 = 9_223_372_036_854_775_807;
            let _: i128 = 170_141_183_460_469_231_731_687_303_715_884_105_727;
            let _: isize = 9_223_372_036_854_775_807;

            let _: i8 = -128;
            let _: i16 = -32_768;
            let _: i32 = -2_147_483_648;
            let _: i64 = -9_223_372_036_854_775_808;
            let _: i128 = -170_141_183_460_469_231_731_687_303_715_884_105_728;
            let _: isize = -9_223_372_036_854_775_808;
        }
    """)

    fun `test overflow in arithmetic operations`() = checkByText("""
        fn main() {
            (<error descr="This arithmetic operation will overflow">0u8 - 1</error>) + (<error descr="This arithmetic operation will overflow">127i8 + 123</error>);
            (<error descr="This arithmetic operation will overflow">256u16 * 256</error>) + (<error descr="This arithmetic operation will overflow">1 << 500</error>);
        }
    """)

    fun `test literal out of range in const expr`() = checkByText("""
        const C: i8 = <error descr="Literal out of range for i8">128</error>;
        static S: u8 = <error descr="Literal out of range for u8">256</error>;

        #[repr(u8)]
        pub enum E {
            V = <error descr="Literal out of range for u8">0xFF1</error>
        }

        fn foo<const N: i16>() {}

        fn main() {
            foo::<<error descr="Literal out of range for i16">65536</error>>();
        }
    """)

    fun `test overflow in const value evaluation E0080`() = checkByText("""
        static C: u8 = <error descr="Could not evaluate static initializer [E0080]">128 + 128</error>;

        #[repr(u8)]
        pub enum E {
            V = <error descr="Evaluation of constant value failed [E0080]">0xF0 * 0o10</error>
        }

        fn foo<const N: i16>() {}

        fn main() {
            let a: [i8; <error descr="Evaluation of constant value failed [E0080]">1 << 500</error>];
            let b = [1; <error descr="Evaluation of constant value failed [E0080]">1 << 129</error>];

            foo::<{ <error descr="Evaluation of constant value failed [E0080]">256 * 256</error> }>();
        }
    """)

    fun `test overflow in const item initializer`() = checkByText("""
        const C: u8 = <error descr="Any use of this value will cause an error">1 - 2</error>;
    """)

    fun `test allow overflowing literals`() = checkByText("""
        #![allow(overflowing_literals)]

        const C: i8 = 128;
        static S: u8 = 256;

        #[repr(u8)]
        pub enum E {
            V = 0xFF1
        }

        fn foo<const N: i16>() {}

        fn main() {
            let _: u16 = 100_000_000;
            foo::<65536>();
        }
    """)

    fun `test allow arithmetic overflow`() = checkByText("""
        #![allow(arithmetic_overflow)]

        fn main() {
            let _: u16 = 0 - 1;
        }
    """)

    fun `test allow const err`() = checkByText("""
        #![allow(const_err)]

        const C: u8 = 1 - 2;
    """)

    fun `test do not load ast`() = checkByFileTree("""
    //- main.rs
        mod foo;

        /*caret*/const C1: u8 = <error descr="Literal out of range for u8">256</error> + foo::C2;
    //- foo.rs
        pub const C2: u8 = 256;
    """)
}
