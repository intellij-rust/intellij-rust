/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

class RsStatementUpDownMoverTest : RsStatementUpDownMoverTestBase() {
    private val statements = listOf(
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
    )

    fun `test step statement`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement

        """, """
            // - main.rs

            $statement
        """)
        }
    }

    fun `test step over single line function`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            fn foo() { }
        """, """
            // - main.rs
            fn foo() { }
            $statement
        """)
        }
    }

    fun `test step over multi line function`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            fn foo() {

            }
        """, """
            // - main.rs
            fn foo() {

            }
            $statement
        """)
        }
    }

    fun `test step over single line struct`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            struct S;
        """, """
            // - main.rs
            struct S;
            $statement
        """)
        }
    }

    fun `test step over multi line struct`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            struct S {
                test: u32
            }
        """, """
            // - main.rs
            struct S {
                test: u32
            }
            $statement
        """)
        }
    }

    fun `test step over multi line struct with attr`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            #[derive(Debug)]
            struct S {
                test: u32
            }
        """, """
            // - main.rs
            #[derive(Debug)]
            struct S {
                test: u32
            }
            $statement
        """)
        }
    }

    fun `test step over multiline impl`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            struct S;
            $statement
            impl S {

            }
        """, """
            // - main.rs
            struct S;
            impl S {

            }
            $statement
        """)
        }
    }

    fun `test step over multiline trait`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            #[test]
            trait S {

            }
        """, """
            // - main.rs
            #[test]
            trait S {

            }
            $statement
        """)
        }
    }

    fun `test step over multiline macro`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            test! {

            }
        """, """
            // - main.rs
            test! {

            }
            $statement
        """)
        }
    }

    fun `test step over single line macro`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            test!();
        """, """
            // - main.rs
            test!();
            $statement
        """)
        }
    }

    fun `test step over single line macro rules`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            macro_rules! test {}
        """, """
            // - main.rs
            macro_rules! test {}
            $statement
        """)
        }
    }

    fun `test step over multi line macro rules`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            macro_rules! test {

            }
        """, """
            // - main.rs
            macro_rules! test {

            }
            $statement
        """)
        }
    }

    fun `test step over single line mod`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            mod test;
        """, """
            // - main.rs
            mod test;
            $statement
        """)
        }
    }

    fun `test step over multi line mod`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            mod test {

            }
        """, """
            // - main.rs
            mod test {

            }
            $statement
        """)
        }
    }

    fun `test step over use`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            use test::{
                test
            };
        """, """
            // - main.rs
            use test::{
                test
            };
            $statement
        """)
        }
    }

    fun `test step over extern crate`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            $statement
            extern crate test;
        """, """
            // - main.rs
            extern crate test;
            $statement
        """)
        }
    }

    fun `test prevent step out function`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            fn s() {
                $statement
            }
        """, """
            // - main.rs
            fn s() {
                $statement
            }
        """)
        }
    }

    fun `test prevent step out mod`() {
        for (statement in statements.map { it.trim() }) {
            moveBothDirectionTest("""
            // - main.rs
            mod s {
                $statement
            }
        """, """
            // - main.rs
            mod s {
                $statement
            }
        """)
        }
    }
}
