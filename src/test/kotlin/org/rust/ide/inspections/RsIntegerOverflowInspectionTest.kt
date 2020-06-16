/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsIntegerOverflowInspectionTest : RsInspectionsTestBase(RsIntegerOverflowInspection::class) {
    fun `test explicit type`() = checkByText("""
        fn main() {
            let _: u16 = <error descr="literal out of range for u16">100_000_000</error>;
        }
    """)

    fun `test inferred type`() = checkByText("""
        fn main() {
            let _ = <error descr="literal out of range for i32">3_000_000_000</error>;
        }
    """)

    fun `test hexadecimal literal`() = checkByText("""
        fn main() {
            let _: u8 = <error descr="literal out of range for u8">0xFFF</error>;
        }
    """)

    fun `test enum repr`() = checkByText("""
        #[repr(u8)]
        pub enum Color {
            White = <error descr="literal out of range for u8">256</error>,
        }
    """)

    fun `test allow overflow`() = checkByText("""
        #![allow(overflowing_literals)]
        fn main() {
            let _: u16 = 100_000_000;
        }
    """)

    fun `test u8 no overflow`() = checkByText("""
        fn main() {
            0u8;
            255u8;
        }
    """)

    fun `test u8 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for u8">256u8</error>;
        }
    """)

    fun `test u16 no overflow`() = checkByText("""
        fn main() {
            0u16;
            65535u16;
        }
    """)

    fun `test u16 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for u16">65536u16</error>;
        }
    """)

    fun `test u32 no overflow`() = checkByText("""
        fn main() {
            0u32;
            4_294_967_295u32;
        }
    """)

    fun `test u32 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for u32">4_294_967_296u32</error>;
        }
    """)

    fun `test u64 no overflow`() = checkByText("""
        fn main() {
            0u64;
            9_223_372_036_854_775_807u64;
        }
    """)

    fun `test u64 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for u64">9_223_372_036_854_775_807u64</error>;
        }
    """)

    fun `test infer signed literal`() = checkByText("""
        fn main() {
            let _: i8 = <error descr="literal out of range for i8">-129i8</error>;
        }
    """)

    fun `test i8 no overflow`() = checkByText("""
        fn main() {
            -128i8;
            0i8;
            127i8;
        }
    """)

    fun `test i8 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for i8">-129i8</error>;
            <error descr="literal out of range for i8">128i8</error>;
        }
    """)

    fun `test i16 no overflow`() = checkByText("""
        fn main() {
            -32768i16;
            0i16;
            32767i16;
        }
    """)

    fun `test i16 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for i16">-32769i16</error>;
            <error descr="literal out of range for i16">32768i16</error>;
        }
    """)

    fun `test i32 no overflow`() = checkByText("""
        fn main() {
            -2_147_483_648i32;
            0i32;
            2_147_483_647i32;
        }
    """)

    fun `test i32 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for i32">-2_147_483_649i32</error>;
            <error descr="literal out of range for i32">2_147_483_648i32</error>;
        }
    """)

    fun `test i64 no overflow`() = checkByText("""
        fn main() {
            -9_223_372_036_854_775_808i64;
            0i64;
            9_223_372_036_854_775_807i64;
        }
    """)

    fun `test i64 overflow`() = checkByText("""
        fn main() {
            <error descr="literal out of range for i64">-9_223_372_036_854_775_809i64</error>;
            <error descr="literal out of range for i64">9_223_372_036_854_775_808i64</error>;
        }
    """)
}
