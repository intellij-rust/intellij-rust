/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

class RsCommaListElementUpDownMoverTest : RsStatementUpDownMoverTestBase() {
    fun `test function parameter`() = moveDownAndBackUp("""
        fn foo(
            /*caret*/x: i32,
            y: i32,
        ) { }
    """, """
        fn foo(
            y: i32,
            /*caret*/x: i32,
        ) { }
    """)

    fun `test function parameter adds comma 1`() = moveUp("""
        fn foo(
            x: i32,
            /*caret*/y: i32
        ) { }
    """, """
        fn foo(
            /*caret*/y: i32,
            x: i32,
        ) { }
    """)

    fun `test function parameter adds comma 2`() = moveDown("""
        fn foo(
            /*caret*/ x: i32,
            y: i32
        ) { }
    """, """
        fn foo(
            y: i32,
            /*caret*/x: i32,
        ) { }
    """)

    fun `test self parameter`() = moveDownAndBackUp("""
        trait T {
            fn foo(
                /*caret*/&self,
                x: i32
            ) {}
        }
    """, """
        trait T {
            fn foo(
                /*caret*/&self,
                x: i32
            ) {}
        }
    """)

    fun `test prevent step out of parameter list`() {
        val code = """
            fn foo(
                /*caret*/x: i32,
            ) { }
        """
        moveDownAndBackUp(code, code)
    }

    fun `test function argument`() = moveDownAndBackUp("""
        fn main() {
            foo(
                /*caret*/x,
                y,
            );
        }
    """, """
        fn main() {
            foo(
                y,
                /*caret*/x,
            );
        }
    """)

    fun `test function argument adds comma 1`() = moveUp("""
        fn main() {
            foo(
                x,
                /*caret*/y
            );
        }
    """, """
        fn main() {
            foo(
                /*caret*/y,
                x,
            );
        }
    """)

    fun `test function argument adds comma 2`() = moveDown("""
        fn main() {
            foo(
                /*caret*/x,
                y
            );
        }
    """, """
        fn main() {
            foo(
                y,
                /*caret*/x,
            );
        }
    """)

    fun `test prevent step out of argument list`() {
        val code = """
            fn main() {
                foo(
                    /*caret*/x,
                );
            }
        """
        moveDownAndBackUp(code, code)
    }

    fun `test prevent step out of use group`() {
        val code = """
            use foo::{
                /*caret*/foo
            };
        """
        moveDownAndBackUp(code, code)
    }

    fun `test move struct fields`() = moveDownAndBackUp("""
        struct S {
            foo: u32,/*caret*/
            bar: u32,
        }
    """, """
        struct S {
            bar: u32,
            foo: u32,/*caret*/
        }
    """)

    fun `test move struct adds comma 1`() = moveDown("""
        struct S {
            foo: u32,/*caret*/
            bar: u32
        }
    """, """
        struct S {
            bar: u32,
            foo: u32,/*caret*/
        }
    """)

    fun `test move struct adds comma 2`() = moveUp("""
        struct S {
            foo: u32,
            /*caret*/bar: u32
        }
    """, """
        struct S {
            /*caret*/bar: u32,
            foo: u32,
        }
    """)

}
