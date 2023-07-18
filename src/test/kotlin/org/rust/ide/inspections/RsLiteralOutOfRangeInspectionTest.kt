/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsLiteralOutOfRangeInspectionTest: RsInspectionsTestBase(RsLiteralOutOfRangeInspection::class) {

    fun `test declaration max u8`() = checkByText("""
        fn main() {
            let x: u8 = /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/;
        }
    """)

    fun `test declaration good u8`() = checkByText(
        """
        fn main() {
            let x: u8 = 255;
        }
    """)

    fun `test declaration fix type`() = checkFixByText("Change type of `x` to `u16`",
        """
        fn main() {
            let x: u8 = /*error descr="The literal `256` does not fit into the type `u8`"*/256/*caret*//*error**/;
        }
        """, """
        fn main() {
            let x: u16 = 256;
        }
        """)

    fun `test declaration fix bigger type`() = checkFixByText("Change type of `x` to `u64`",
        """
        fn main() {
            let x: u8 = /*error descr="The literal `10000000000` does not fit into the type `u8`"*/10_000_000_000/*caret*//*error**/;
        }
        """, """
        fn main() {
            let x: u64 = 10_000_000_000;
        }
        """.trimIndent())

    fun `test declaration max u16`() = checkByText(
        """
        fn main() {
            let x: u16 = /*error descr="The literal `65536` does not fit into the type `u16`"*/65_536/*error**/;
        }
    """)

    fun `test declaration good u16`() = checkByText(
        """
        fn main() {
            let x: u16 = 65_535;
        }
    """)

    fun `test declaration max u32`() = checkByText(
        """
        fn main() {
            let x: u32 = /*error descr="The literal `4294967296` does not fit into the type `u32`"*/4_294_967_296/*error**/;
        }
    """)

    fun `test declaration good u32`() = checkByText(
        """
        fn main() {
            let x: u32 = 4_294_967_295;
        }
    """)

    fun `test simple assignment to u8`() = checkByText(
        """
        fn main() {
            let x: u8;
            x = /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/;
        }
    """)

    fun `test simple struct literal`() = checkByText(
        """
        struct Foo {
            x: u8
        }
        fn main() {
            let f = Foo { x: /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/ };
        }
    """)

    fun `test struct literal fix`() = checkFixByText("Change type of `x` to `u16`",
        """
        struct Foo {
            x: u8
        }
        fn main() {
            let f = Foo { x: /*error descr="The literal `256` does not fit into the type `u8`"*/256/*caret*//*error**/ };
        }
        """, """
        struct Foo {
            x: u16
        }
        fn main() {
            let f = Foo { x: 256 };
        }
        """.trimIndent())

    fun `test multiple fields struct literal`() = checkByText(
        """
        struct Foo {
            x: u8,
            y: u8
        }
        fn main() {
            let f = Foo { x: 255, y: /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/ };
        }
    """)

    fun `test multiple error struct literal`() = checkByText(
        """
        struct Foo {
            x: u16,
            y: u8
        }
        fn main() {
            let f = Foo {
                x: /*error descr="The literal `100000000` does not fit into the type `u16`"*/100_000_000/*error**/,
                y: /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/ };
        }
    """)

    fun `test indirect struct literal`() = checkByText(
        """
        struct Foo {
            x: u8
        }
        fn main() {
            let x = /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/;
            let f = Foo { x };
        }
    """)

    fun `test function call`() = checkByText(
        """
        fn f(arg: u8) -> u8 {
            arg
        }

        fn main() {
            f(/*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/);
        }
    """)

    fun `test function call many arguments`() = checkByText(
        """
        fn f(arg1: u16, arg2: u8) -> u8 {
            arg
        }

        fn main() {
            f(256, /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/);
        }
    """)

    fun `test function call indirect`() = checkByText(
        """
        fn f(arg: u8) -> u8 {
            arg
        }

        fn main() {
            let a = /*error descr="The literal `256` does not fit into the type `u8`"*/256/*error**/;
            f(a);
        }
    """)
}
