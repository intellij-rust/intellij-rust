/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsReassignImmutableInspectionTest : RsInspectionsTestBase(RsReassignImmutableInspection()) {

    fun `test E0384 reassign immutable binding`() = checkByText("""
        fn main() {
            let x = 5;
            <error descr="Re-assignment of immutable variable [E0384]">x = 3</error>;
        }
    """)

    fun `test E0384 reassign mutable binding`() = checkByText("""
        fn main() {
            let mut x = 5;
            x = 3;
        }
    """)

    fun `test E0384 reassign mutable static`() = checkByText("""
        fn main() {
            static mut X: u32 = 5;
            X = 3;
        }
    """)

    fun `test E0384 assign mutable binding later`() = checkByText("""
        fn main() {
            let mut x;
            x = 3;
        }
    """)

    fun `test E0384 assign immutbable binding later`() = checkByText("""
        fn main() {
            let x;
            x = 3;
        }
    """)

    fun `test E0384 reassign mutable binding via asterisk`() = checkByText("""
        fn main() {
            let mut x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun `test E0384 reassign immutable binding via asterisk`() = checkByText("""
        fn main() {
            let x = 3;
            {
                let y = &x;
                *y = 5;
            }
        }
    """)

    fun `test E0384 in pattern`() = checkByText("""
        fn main() {
            let (x, mut y) = (92, 62);
            <error descr="Re-assignment of immutable variable [E0384]">x = 42</error>;
            y = 42;
        }
    """)

    fun `test E0384 mutable used at mutable function definition`() = checkByText("""
        fn test(mut test: i32) {
            test = 10
        }
    """)

    fun `test E0384 immutable used at mutable function definition`() = checkByText("""
        fn test(test: i32) {
            <error descr="Re-assignment of immutable variable [E0384]">test = 10</error>;
        }
    """)

    fun `test E0384 immutable used at mutable function definition (pattern)`() = checkByText("""
        fn foo((x, y): (i32, i32)) {
            <error descr="Re-assignment of immutable variable [E0384]">x = 92</error>;
        }
    """)

    fun `test E0384 mutable used at mutable function definition (pattern)`() = checkByText("""
        fn foo((mut x, y): (i32, i32)) {
            x = 92;
        }
    """)

    fun `test E0384 immutable used at mutable function definition (pattern) 2`() = checkByText("""
        fn foo((x, y): (i32, i32)) {
            <error descr="Re-assignment of immutable variable [E0384]">y = 92</error>;
        }
    """)

    fun `test E0384 mutable used at mutable function definition (pattern) 2`() = checkByText("""
        fn foo((x, mut y): (i32, i32)) {
            y = 92;
        }
    """)

    fun `test E0384 no error in for loop over mutable`() = checkByText("""
        struct Foo { a: u32 }
        fn main() {
            let mut vec: Vec<Foo> = Vec::new();
            for v in &mut vec {
                v.a = 15;           // Must not be annotated
            }
        }
    """)

    fun `test E0384 fix let at reassign`() = checkFixByText("Make `test` mutable", """
        fn main() {
            let test = 10;
            <error>t<caret>est = 5</error>;
        }
    """, """
        fn main() {
            let mut test = 10;
            test = 5;
        }
    """)

    fun `test fix E0384 let pattern at reassign`() = checkFixByText("Make `test` mutable", """
        fn main() {
            let (test, test2) = (10, 20);
            <error>te<caret>st = 5</error>;
        }
    """, """
        fn main() {
            let (mut test, test2) = (10, 20);
            test = 5;
        }
    """)

    fun `test fix E0384 let pattern at reassign 2`() = checkFixByText("Make `test2` mutable", """
        fn main() {
            let (test, test2) = (10, 20);
            <error>tes<caret>t2 = 5</error>;
        }
    """, """
        fn main() {
            let (test, mut test2) = (10, 20);
            test2 = 5;
        }
    """)

    fun `test E0384 fix method at call (args)`() = checkFixByText("Make `test` mutable", """
        fn test(test: i32) {
            <error>te<caret>st = 32</error>;
        }
    """, """
        fn test(mut test: i32) {
            test = 32;
        }
    """)

}
