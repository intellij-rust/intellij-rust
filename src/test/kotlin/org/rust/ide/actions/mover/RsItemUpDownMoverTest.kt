/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import org.intellij.lang.annotations.Language

class RsItemUpDownMoverTest : RsStatementUpDownMoverTestBase() {
    fun `test step nowhere`() = doTest("""
        /*item*/
    """, """
        /*item*/
    """)

    fun `test step comment`() = doTest("""
        /*item*/

        //EOF
    """, """
        //EOF

        /*item*/
    """)

    fun `test move struct out of function`() = moveDown("""
        fn s() {
            struct /*caret*/Foo {
                pub f: u32,
            }
        }
    """, """
        fn s() {
        }
        struct /*caret*/Foo {
            pub f: u32,
        }
    """)

    fun `test move function out of mod`() = moveDown("""
        mod s {
            fn /*caret*/foo() {}
        }
    """, """
        mod s {
        }
        fn /*caret*/foo() {}
    """)

    fun `test impl prevent step out`() {
        val code = """
            struct S;
            impl S {
                fn /*caret*/test() {
                    test!();
                }
            }
        """
        UpDownMoverTestMarks.moveOutOfImpl.checkHit {
            moveDownAndBackUp(code, code)
        }
    }

    fun `test trait prevent step out`() {
        val code = """
            trait T {
                type /*caret*/Foo;
            }
        """
        UpDownMoverTestMarks.moveOutOfImpl.checkHit {
            moveDownAndBackUp(code, code)
        }
    }

    fun `test can move items inside impl`() {
        moveDownAndBackUp("""
            impl s {
                const /*caret*/FOO: i32 = 92;


                fn foo() {

                }
            }
        """, """
            impl s {
                fn foo() {

                }


                const /*caret*/FOO: i32 = 92;
            }
        """)
    }

    //TODO: all tests bellow are way to similar.
    // Could we reduce test duplication here?

    fun `test step over single line function 2`() = doTest("""
        /*item*/
        fn foo() { }
    """, """
        fn foo() { }
        /*item*/
    """)

    fun `test step over multi line function`() = doTest("""
        /*item*/
        fn foo() {

        }
    """, """
        fn foo() {

        }
        /*item*/
    """)

    fun `test step over single line struct`() = doTest("""
        /*item*/
        struct S;
    """, """
        struct S;
        /*item*/
    """)

    fun `test step over multi line struct`() = doTest("""
        /*item*/
        struct S {
        test: u32
        }
    """, """
        struct S {
        test: u32
        }
        /*item*/
    """)

    fun `test step over multi line struct with attr`() = doTest("""
        /*item*/
        #[derive(Debug)]
        struct S {
        test: u32
        }
    """, """
        #[derive(Debug)]
        struct S {
        test: u32
        }
        /*item*/
    """)

    fun `test step over multiline impl`() = doTest("""
        struct S;
        /*item*/
        impl S {

        }
    """, """
        struct S;
        impl S {

        }
        /*item*/
    """)

    fun `test step over multiline trait`() = doTest("""
        /*item*/
        #[test]
        trait S {

        }
    """, """
        #[test]
        trait S {

        }
        /*item*/
    """)

    fun `test step over multiline macro`() = doTest("""
        /*item*/
        test! {

        }
    """, """
        test! {

        }
        /*item*/
    """)

    fun `test step over single line macro`() = doTest("""
        /*item*/
        test!();
    """, """
        test!();
        /*item*/
    """)

    fun `test step over single line macro rules`() = doTest("""
        /*item*/
        macro_rules! test {}
    """, """
        macro_rules! test {}
        /*item*/
    """)

    fun `test step over multi line macro rules`() = doTest("""
        /*item*/
        macro_rules! test {

        }
    """, """
        macro_rules! test {

        }
        /*item*/
    """)

    fun `test step over single line mod`() = doTest("""
        /*item*/
        mod test;
    """, """
        mod test;
        /*item*/
    """)

    fun `test step over multi line mod`() = doTest("""
        /*item*/
        mod test {

        }
    """, """
        mod test {

        }
        /*item*/
    """)

    fun `test step over use`() = doTest("""
        /*item*/
        use test::
            test;
    """, """
        use test::
            test;
        /*item*/
    """)

    fun `test step over extern crate`() = doTest("""
        /*item*/
        extern crate test;
    """, """
        extern crate test;
        /*item*/
    """)

    private val items = listOf(
        """
            struct /*caret*/ A {
                test: u32
            }
        """,
        """
            struct /*caret*/ A;
        """,
        """
            #[derive(Debug)]
            struct /*caret*/ A {
                test: u32
            }
        """,
        """
            fn /*caret*/test() {

            }
        """,
        """
            fn <selection>test() {</selection>

            }
        """,
        """
            #[test]
            fn /*caret*/test() {

            }
        """,
        """
            fn /*caret*/test() { }
        """,
        """
            impl /*caret*/Test {

            }
        """,
        """
            trait /*caret*/Test {

            }
        """,
        """
            mod /*caret*/test {

            }
        """,
        """
            test! /*caret*/ {

            }
        """,
        """
            test! /*caret*/ ();
        """,
        """
            macro_rules! test/*caret*/ {

            }
        """,
        """
            use test::{
                test
            };
        """
    ).map { it.trimIndent() }

    private fun doTest(@Language("Rust") _a: String, @Language("Rust") _b: String) {
        val placeholder = "/*item*/"
        fun replacePlaceholder(_text: String, replacement: String): String {
            val text = _text.trimIndent()
            val indent = text.lines().find { placeholder in it }!!.substringBefore(placeholder)
            return text.replace("$indent$placeholder", replacement.trimIndent().lines().joinToString("\n") { "$indent$it" })
        }

        for (item in items) {
            val a = replacePlaceholder(_a, item)
            val b = replacePlaceholder(_b, item)
            moveDownAndBackUp(a, b)
        }
    }
}
