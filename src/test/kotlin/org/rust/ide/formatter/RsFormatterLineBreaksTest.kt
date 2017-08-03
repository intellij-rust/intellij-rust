/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsFormatterLineBreaksTest : RsFormatterTestBase() {
    override fun getTestDataPath() = "src/test/resources"

    override fun getBasePath() = "org/rust/ide/formatter/fixtures/line_breaks"

    override fun getFileExtension() = "rs"

    fun testAll() = doTest()
    fun testTraits() = doTest()

    fun testBlocks() = doTextTest("""
        fn main() {
            let foo = { foo(123, 456,
                            789) };
        }
    """, """
        fn main() {
            let foo = {
                foo(123, 456,
                    789)
            };
        }
    """)

    fun testBlocks2() = checkNotChanged("""
        fn main() {
            let foo = { foo(123, 456, 789) };
        }
    """)

    fun `test multiline blocks`() = doTextTest("""
        struct S1 { f: i32 }
        struct S2 {
        f: i32}
        struct S3 {f: i32
        }

        enum E {
        V{f:i32},
        X{f: i32,
        x: i32}}

        trait Empty { /*bla-bla-bla*/ }

        trait HasStuff { fn foo();
          fn bar();
          fn baz();}

        fn main() {
            let _ = || { /*comment*/ foo(); };
            let _  = || {
                92};
        }
    """, """
        struct S1 { f: i32 }

        struct S2 {
            f: i32
        }

        struct S3 {
            f: i32
        }

        enum E {
            V { f: i32 },
            X {
                f: i32,
                x: i32
            }
        }

        trait Empty { /*bla-bla-bla*/ }

        trait HasStuff {
            fn foo();
            fn bar();
            fn baz();
        }

        fn main() {
            let _ = || { /*comment*/ foo(); };
            let _ = || {
                92
            };
        }
""")
}
