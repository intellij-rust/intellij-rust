package org.rust.ide.formatter

class RsCommaFormatProcessorTest : RsFormatterTestBase() {
    fun testRemovesCommaIfSingleLine() = doTextTest("""
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
