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
                b: /*caret*/(),
                c: 92,
                d: (),
            };
        }
    """)

    fun testFirstFieldIsAddedFirst() = checkQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { b: 0,/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: /*caret*/(), b: 0 };
        }
    """)

    fun testLastFieldIsAddedLast() = checkQuickFix("""
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { /*caret*/a: 0 };
        }
    """, """
        struct S { a: i32, b: i32 }

        fn main() {
            let _ = S { a: 0, b: /*caret*/() };
        }
    """)

    fun testPreservesOrder() = checkQuickFix("""
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = S { a: 0, c: 1, e: 2/*caret*/ };
        }
    """, """
        struct S { a: i32, b: i32, c: i32, d: i32, e: i32}

        fn main() {
            let _ = S { a: 0, b: /*caret*/(), c: 1, d: (), e: 2 };
        }
    """)

    private fun checkQuickFix(@Language("Rust") before: String, @Language("Rust") after: String) =
        checkQuickFix("Add missing fields", before, after)

}
