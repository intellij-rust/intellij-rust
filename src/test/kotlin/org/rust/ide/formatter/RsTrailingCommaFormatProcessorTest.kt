/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.formatter

class RsTrailingCommaFormatProcessorTest : RsFormatterTestBase() {
    fun testRemovesCommaIfSingleLine() = doTextTest("""
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
}
