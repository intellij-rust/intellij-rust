/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions.mover

import org.rust.CheckTestmarkHit

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


    fun `test function parameter adds comma 3`() = moveUp("""
        fn foo(
            x: i32,
            /*caret*/y: i32, z: i32
        ) { }
    """, """
        fn foo(
            /*caret*/y: i32, z: i32,
            x: i32,
        ) { }
    """)

    fun `test function parameter adds comma 4`() = moveDown("""
        fn foo(
            /*caret*/ x: i32,
            y: i32, z: i32
        ) { }
    """, """
        fn foo(
            y: i32, z: i32,
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

    fun `test prevent step out of parameter list`() = moveDownAndBackUp("""
        fn foo(
            /*caret*/x: i32,
        ) { }
    """)

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

    fun `test function argument adds comma 3`() = moveUp("""
        fn main() {
            foo(
                x,
                /*caret*/y, z
            );
        }
    """, """
        fn main() {
            foo(
                /*caret*/y, z,
                x,
            );
        }
    """)

    fun `test function argument adds comma 4`() = moveDown("""
        fn main() {
            foo(
                /*caret*/x,
                y, z
            );
        }
    """, """
        fn main() {
            foo(
                y, z,
                /*caret*/x,
            );
        }
    """)

    fun `test prevent step out of argument list`() = moveDownAndBackUp("""
        fn main() {
            foo(
                /*caret*/x,
            );
        }
    """)

    fun `test prevent step out of use group`() = moveDownAndBackUp("""
        use foo::{
            /*caret*/foo
        };
    """)

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

    fun `test move struct adds comma 3`() = moveDown("""
        struct S {
            foo: u32,/*caret*/
            bar: u32, baz: u32
        }
    """, """
        struct S {
            bar: u32, baz: u32,
            foo: u32,/*caret*/
        }
    """)

    fun `test move struct adds comma 4`() = moveUp("""
        struct S {
            foo: u32,
            /*caret*/bar: u32, baz: u32
        }
    """, """
        struct S {
            /*caret*/bar: u32, baz: u32,
            foo: u32,
        }
    """)

    fun `test move vec argument`() = moveDownAndBackUp("""
        fn foo() {
            bar(
                Baz {
                    x
                }, vec![
                    1,/*caret*/
                    2,
                    3,
                ],
            );
        }
    """, """
        fn foo() {
            bar(
                Baz {
                    x
                }, vec![
                    2,
                    1,/*caret*/
                    3,
                ],
            );
        }
    """)

    @CheckTestmarkHit(UpDownMoverTestMarks.MoveOutOfBlock::class)
    fun `test move up first vec argument`() = moveUp("""
        fn foo() {
            bar(
                Baz {
                    x
                }, vec![
                    1,/*caret*/
                    2,
                    3,
                ],
            );
        }
    """)

    @CheckTestmarkHit(UpDownMoverTestMarks.MoveOutOfBlock::class)
    fun `test move down last vec argument`() = moveDown("""
        fn foo() {
            bar(
                Baz {
                    x
                }, vec![
                    1,
                    2,
                    3,/*caret*/
                ],
            );
        }
    """)

    fun `test move vec adds comma 1`() = moveDown("""
        fn main() {
            let v = vec![
                "a",
                /*caret*/"b",
                "c"
            ];
        }
    """, """
        fn main() {
            let v = vec![
                "a",
                "c",
                /*caret*/"b",
            ];
        }
    """)

    fun `test move vec adds comma 2`() = moveUp("""
        fn main() {
            let v = vec![
                "a",
                "b",
                /*caret*/"c"
            ];
        }
    """, """
        fn main() {
            let v = vec![
                "a",
                /*caret*/"c",
                "b",
            ];
        }
    """)

    fun `test move vec adds comma 3`() = moveDown("""
        fn main() {
            let v = vec![
                "a",
                /*caret*/"b",
                "c", "d"
            ];
        }
    """, """
        fn main() {
            let v = vec![
                "a",
                "c", "d",
                /*caret*/"b",
            ];
        }
    """)

    fun `test move vec adds comma 4`() = moveUp("""
        fn main() {
            let v = vec![
                "a",
                "b",
                /*caret*/"c", "d"
            ];
        }
    """, """
        fn main() {
            let v = vec![
                "a",
                /*caret*/"c", "d",
                "b",
            ];
        }
    """)

    fun `test move vec adds comma 5`() = moveDown("""
        fn main() {
            let v = vec![
                1,
                2,/*caret*/
                {
                    3
                }, { 4 }
            ];
        }
    """, """
        fn main() {
            let v = vec![
                1,
                {
                    3
                }, { 4 },
                2,/*caret*/
            ];
        }
    """)
}
