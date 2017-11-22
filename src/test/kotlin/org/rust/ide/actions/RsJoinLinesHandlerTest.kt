/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

class RsJoinLinesHandlerTest : RsJoinLinesHandlerTestBase() {
    fun `test empty file`() = doTestRaw ("/*caret*/", "/*caret*/")

    fun `test blank file1`() = doTestRaw("/*caret*/\n\n", "/*caret*/\n")
    fun `test blank file2`() = doTestRaw("\n/*caret*/\n", "\n/*caret*/")

    fun `test no escape`() = doTest("""
        fn main() {
            "Hello<caret>,
             World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun `test newline escape`() = doTest("""
        fn main() {
            "He<caret>llo, \
             World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun `test escaped newline escape`() = doTest("""
        fn main() {
            "He<caret>llo, \\
             World"
        }
    """, """
        fn main() {
            "Hello, \\<caret> World"
        }
    """)

    fun `test escaped but not escaped in fact newline escape`() = doTest("""
        fn main() {
            "He<caret>llo, \\\
             World"
        }
    """, """
        fn main() {
            "Hello, \\<caret> World"
        }
    """)

    fun `test two escaped backslashes`() = doTest("""
        fn main() {
            "He<caret>llo, \\\\
             World"
        }
    """, """
        fn main() {
            "Hello, \\\\<caret> World"
        }
    """)

    fun `test no indent`() = doTest("""
        fn main() {
            "Hel<caret>lo,
        World"
        }
    """, """
        fn main() {
            "Hello,<caret> World"
        }
    """)

    fun `test only newline escape`() = doTest("""
        fn main() {
            "<caret>\
            "
        }
    """, """
        fn main() {
            "<caret> "
        }
    """)

    fun `test outer doc comment`() = doTest("""
        /// Hello<caret>
        /// Docs
        fn foo() {}
    """, """
        /// Hello<caret> Docs
        fn foo() {}
    """)

    fun `test inner doc comment`() = doTest("""
        //! Hello<caret>
        //! Docs
    """, """
        //! Hello<caret> Docs
    """)

    fun `test outer doc comment not comment`() = doTest("""
        /// Hello<caret>
        fn foo() {}
    """, """
        /// Hello<caret> fn foo() {}
    """)

    fun `test join struct selection`() =
        doTest("""
            struct S { foo: i32, bar: i32 }
            fn main() {
                let _ = S <selection>{
                    foo: 42,
                    bar: 42,
                };</selection>
            }
        ""","""
            struct S { foo: i32, bar: i32 }
            fn main() {
                let _ = S { foo: 42, bar: 42 };
            }
        """)

    fun `test join struct`() = doTest("""
        struct S { foo: i32 }
        fn main() {
            let _ = S {/*caret*/
                foo: 42,
            };
        }
    ""","""
        struct S { foo: i32 }
        fn main() {
            let _ = S {/*caret*/ foo: 42,
            };
        }
    """)

    fun `test remove comma struct literal 1`() = doTest("""
        struct S { foo: i32 }
        fn main() {
            let _ = S { foo: 42,/*caret*/
             };
        }
    ""","""
        struct S { foo: i32 }
        fn main() {
            let _ = S { foo: 42/*caret*/ };
        }
    """)

    fun `test remove comma struct literal 2`() = doTest("""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S {
                foo: 42,
                bar: /*caret*/42,
             };
        }
    ""","""
        struct S { foo: i32, bar: i32 }
        fn main() {
            let _ = S {
                foo: 42,
                bar: 42/*caret*/ };
        }
    """)

    fun `test remove comma struct definition`() = doTest("""
        /*caret*/struct S { foo: i32,
        }
    ""","""
        struct S { foo: i32/*caret*/ }
    """)

    fun `test remove comma tuple struct definition`() = doTest("""
        /*caret*/struct S(i32,
        );
    ""","""
        struct S(i32/*caret*/);
    """)

    fun `test remove comma function parameter`() = doTest("""
        /*caret*/fn foo (foo: i32,
        ) {}
    ""","""
        fn foo (foo: i32/*caret*/) {}
    """)

    fun `test remove comma function call`() = doTest("""
        fn main() {
            foo(1,/*caret*/
            )
        }
    ""","""
        fn main() {
            foo(1/*caret*/)
        }
    """)

    fun `test don't remove comma from tuple`() = doTest("""
        fn main() {
            /*caret*/let _ = (1,
            );
        }
    """, """
        fn main() {
            let _ = (1,/*caret*/ );
        }
    """)

    fun `test remove comma from enum definition`() = doTest("""
        enum E { X,/*caret*/ Y,
        }
    """, """
        enum E { X, Y/*caret*/ }
    """)

    fun `test remove comma from use glob`() = doTest("""
        use foo::{bar,/*caret*/ baz,
        };
    """, """
        use foo::{bar, baz/*caret*/};
    """)
}
