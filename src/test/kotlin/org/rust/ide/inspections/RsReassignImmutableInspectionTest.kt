/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsReassignImmutableInspectionTest : RsInspectionsTestBase(RsReassignImmutableInspection()) {

    fun `test E0384 reassign immutable binding`() = checkByText("""
        fn main() {
            let x = 5;
            <error descr="Cannot assign twice to immutable variable [E0384]">x = 3</error>;
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
            unsafe { X = 3; }
        }
    """)

    fun `test E0384 assign mutable binding later`() = checkByText("""
        fn main() {
            let mut x;
            x = 3;
        }
    """)

    fun `test E0384 assign immutable binding later`() = checkByText("""
        fn main() {
            let x;
            x = 3;
        }
    """)

    fun `test E0384 assign immutable binding later for reference type`() = checkByText("""
        fn main() {
            let x;
            x = &42;
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
            <error descr="Cannot assign twice to immutable variable [E0384]">x = 42</error>;
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
            <error descr="Cannot assign twice to immutable variable [E0384]">test = 10</error>;
        }
    """)

    fun `test E0384 immutable used at mutable function definition (pattern)`() = checkByText("""
        fn foo((x, y): (i32, i32)) {
            <error descr="Cannot assign twice to immutable variable [E0384]">x = 92</error>;
        }
    """)

    fun `test E0384 mutable used at mutable function definition (pattern)`() = checkByText("""
        fn foo((mut x, y): (i32, i32)) {
            x = 92;
        }
    """)

    fun `test E0384 immutable used at mutable function definition (pattern) 2`() = checkByText("""
        fn foo((x, y): (i32, i32)) {
            <error descr="Cannot assign twice to immutable variable [E0384]">y = 92</error>;
        }
    """)

    fun `test E0384 mutable used at mutable function definition (pattern) 2`() = checkByText("""
        fn foo((x, mut y): (i32, i32)) {
            y = 92;
        }
    """)

    fun `test E0384 mut struct field`() = checkByText("""
        struct Foo { a: i32 }
        fn main() {
            let mut foo = Foo { a: 1 };
            let x = &mut foo;
            x.a = 2;
        }
    """)

    fun `test E0384 reassign ref mut`() = checkByText("""
        fn main() {
            let mut a = 5;
            let mut b = 6;
            let ref mut test = a;
            <error>test<caret> = &mut b</error>;
        }
    """)
}
