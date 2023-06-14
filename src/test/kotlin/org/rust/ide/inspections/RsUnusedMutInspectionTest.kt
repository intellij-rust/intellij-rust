/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

class RsUnusedMutInspectionTest : RsInspectionsTestBase(RsUnusedMutInspection::class) {

    fun `test should annotate unused variable`() = checkByText("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
        }
    """)

    fun `test should not annotate if there is not mut`() = checkByText("""
        fn main() {
            let f = 10;
        }
    """)

    fun `test should not annotate self methods`() = checkByText("""
        struct Foo { foo: bool }
        impl Foo {
            fn push(&mut self, test: i32) {
            }
        }
        fn foo() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ t = 10;
            let mut f = Foo { foo: true };
            f.push(t);
        }
    """)

    fun `test should not annotate mutated fields`() = checkByText("""
        struct Foo { foo: bool }
        fn foo(b: &mut bool) {}
        fn bar() {
            let mut f = Foo { foo: true };
            foo(&mut f.foo);
        }
    """)

    fun `test should annotate mutated parameter`() = checkByText("""
        fn foo(i: i32) {
            println!("{:?}", i);
        }

        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
            foo(f);
        }
    """)

    fun `test should not annotate mutated parameter`() = checkByText("""
        fn foo(i: &mut i32) {
            *i = 20;
        }

        fn main() {
            let mut f = 10;
            foo(&mut f);
        }
    """)

    fun `test first mut and second not mut`() = checkByText("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
            let mut f = 10;
            f = 20;
        }
    """)

    fun `test tuple`() = checkByText("""
        fn bar(s: &mut (u8, u8)) {}

        fn main() {
            let mut a = 1u8;
            let mut b = 2u8;
            bar(&mut (a, b));
        }
    """)

    fun `test match arms`() = checkByText("""
        fn main() {
            let mut a = Some(20);
            if let Some(ref mut b) = a { *b = 10; }

            match a {
                Some(ref mut b) => *b = 10,
                None => {},
            }
        }
    """)

    fun `test should not annotate if macro after expr`() = checkByText("""
        fn foo(a: &mut u32) { }

        macro_rules! call_foo { () => { w = 20; } }

        fn main() {
            let mut w = 10;
            call_foo!();
        }
    """)

    fun `test should annotate if macro before expr`() = checkByText("""
        fn foo(a: &mut u32) { }

        macro_rules! call_foo { () => { let mut w = 20; w = 20; } }

        fn main() {
            call_foo!();
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**//*caret*/ f = 10;
        }
    """)

    fun `test remove mut function parameter`() = checkFixByText("Remove `mut`", """
        fn foo(mut test: i32) {
            test = 10;
        }
        fn foo2(/*weak_warning descr="Unused `mut`"*//*caret*/mut/*weak_warning**/ test: i32) {
        }
    """, """
        fn foo(mut test: i32) {
            test = 10;
        }
        fn foo2(test: i32) {
        }
    """)

    fun `test remove only unused mut`() = checkFixByText("Remove `mut`", """
        fn main() {
            let mut f = 10;
            f = 20;
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**//*caret*/ f = 10;
        }
    """, """
        fn main() {
            let mut f = 10;
            f = 20;
            let f = 10;
        }
    """)

    fun `test function`() = checkByText("""
        fn main() {
            let mut a = 10;
            let mut foo = || a = 20;
            foo();
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test should not annotate in comments`() = checkByFileTree("""
        //- lib.rs
        struct A {}
        impl A {
            /// ## Example
            /// ```rust
            /// let mut a = 1;
            /// ```
            pub fn foo() {}
        }
        /*caret*/
    """, checkWeakWarn = true)
}
