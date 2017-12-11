/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsTrailingCommaFormatProcessorTest : RsFormatterTestBase() {
    fun `test removes trailing comma if single line in brace blocks`() = doTextTest("""
        use foo::{bar, baz,};

        struct S1 { a: i32, }

        struct S2 { a: i32 }

        enum E {
            V { a: i32, }
        }

        fn main() {
            let _ = S {};
            let _ = S { x: 92 };
            let _ = S { x: 92, };
            let _ = S { x: 92, y: 62 };
            let _ = S { x: 92, y: 62, };
            let _ = S {
                x: 92,
            };

            let _ = S {
                x: 92
            };
        }
    """, """
        use foo::{bar, baz};

        struct S1 { a: i32 }

        struct S2 { a: i32 }

        enum E {
            V { a: i32 }
        }

        fn main() {
            let _ = S {};
            let _ = S { x: 92 };
            let _ = S { x: 92 };
            let _ = S { x: 92, y: 62 };
            let _ = S { x: 92, y: 62 };
            let _ = S {
                x: 92,
            };

            let _ = S {
                x: 92
            };
        }
    """)

    fun `test removes trailing comma if single in parent blocks`() = doTextTest("""
        struct T(i32,);

        fn f(x: i32,) {
            foo(1,);
            foo.bar(1, 2,);
        }
    """, """
        struct T(i32);

        fn f(x: i32) {
            foo(1);
            foo.bar(1, 2);
        }
    """)

    fun `test adds trailing commas in multiline blocks blocks`() = doTextTest("""
        struct T1(
            i32
        );

        struct T2(
            i32,
            i32
        );

        fn f(
            x: i32,
            y: i32
        ) {
            foo(
                1
            );
            foo.bar(
                1,
                2
            );
        }
    """, """
        struct T1(
            i32
        );

        struct T2(
            i32,
            i32,
        );

        fn f(
            x: i32,
            y: i32,
        ) {
            foo(
                1
            );
            foo.bar(
                1,
                2,
            );
        }
    """)
}
