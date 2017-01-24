package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.ide.annotator.RsAnnotatorTestBase

class AddStructFieldsFixTest : RsAnnotatorTestBase() {
    fun testNoFields() = checkQuickFix("""
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

    fun testNoComma() = checkQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92/*caret*/};
        }
        """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: /*caret*/() };
        }
        """)

    fun testWithComma() = checkQuickFix("""
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, /*caret*/};
        }
        """, """
        struct S { a: i32, b: String }

        fn main() {
            S { a: 92, b: /*caret*/() };
        }
        """)

    fun testSomeExistingFields() = checkQuickFix("""
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
                a: 92,
                c: 92,
                b: /*caret*/(),
                d: (),
            };
        }
    """)

    private fun checkQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkQuickFix("Add missing fields", before, after)

}
