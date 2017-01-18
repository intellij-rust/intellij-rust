package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase

class AddStructFieldsFixTest : RsAnnotatorTestBase() {
    fun testNoFields() = checkQuickFix("Add missing fields", """
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = S { /*caret*/ };
        }
    """, """
        struct S { foo: i32, bar: f64 }

        fn main() {
            let _ = S {
                foo: /*caret*/(),
                bar: (),
            };
        }
    """)

    fun testSomeExistingFields() = checkQuickFix("Add missing fields", """
        struct S { a: i32, b: i32, c: i32, d: i32 }

        fn main() {
            let _ = S {
                a: 92,
                c: 92/*caret*/
            };
        }
    """, """
        struct S { a: i32, b: i32, c: i32, d: i32 }

        fn main() {
            let _ = S {
                b: /*caret*/(),
                d: (),
                a: 92,
                c: 92
            };
        }
    """)
}
