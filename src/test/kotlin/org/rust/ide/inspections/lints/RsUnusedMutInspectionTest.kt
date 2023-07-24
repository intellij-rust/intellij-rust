/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

class RsUnusedMutInspectionTest : RsInspectionsTestBase(RsUnusedMutInspection::class) {

    fun `test should annotate unused variable`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
        }
    """)

    fun `test should not annotate if there is not mut`() = checkWarnings("""
        fn main() {
            let f = 10;
        }
    """)

    fun `test should not annotate self methods`() = checkWarnings("""
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

    fun `test should not annotate mutated fields`() = checkWarnings("""
        struct Foo { foo: bool }
        fn foo(b: &mut bool) {}
        fn bar() {
            let mut f = Foo { foo: true };
            foo(&mut f.foo);
        }
    """)

    fun `test should annotate mutated parameter`() = checkWarnings("""
        fn foo(i: i32) {
            println!("{:?}", i);
        }

        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
            foo(f);
        }
    """)

    fun `test should not annotate mutated parameter`() = checkWarnings("""
        fn foo(i: &mut i32) {
            *i = 20;
        }

        fn main() {
            let mut f = 10;
            foo(&mut f);
        }
    """)

    fun `test first mut and second not mut`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ f = 10;
            let mut f = 10;
            f = 20;
        }
    """)

    fun `test tuple`() = checkWarnings("""
        fn bar(s: &mut (u8, u8)) {}

        fn main() {
            let mut a = 1u8;
            let mut b = 2u8;
            bar(&mut (a, b));
        }
    """)

    fun `test match arms`() = checkWarnings("""
        fn main() {
            let mut a = Some(20);
            if let Some(ref mut b) = a { *b = 10; }

            match a {
                Some(ref mut b) => *b = 10,
                None => {},
            }
        }
    """)

    fun `test should not annotate if macro after expr`() = checkWarnings("""
        macro_rules! assign { ($ i:ident) => { $ i = 20; } }

        fn main() {
            let mut w = 10;
            assign!(w);
        }
    """)

    fun `test should annotate if macro before expr`() = checkWarnings("""
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
    """, checkWeakWarn = true)

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
    """, checkWeakWarn = true)

    fun `test function`() = checkWarnings("""
        fn main() {
            let mut a = 10;
            let mut foo = || a = 20;
            foo();
        }
    """)

    fun `test mutation in macro`() = checkWarnings("""
        macro_rules! assign {
            ($ i:ident = $ e:expr) => { $ i = $ e; };
        }
        fn main() {
            let mut a = 1;
            assign!(a = 2);
        }
    """)

    fun `test no mutation in macro`() = checkWarnings("""
        macro_rules! assign {
            ($ i:ident = $ e:expr) => { $ i = $ e; };
        }
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = 1;
            let mut b = 2;
            assign!(b = a);
        }
    """)

    fun `test format macro argument`() = checkWarnings("""
        use std::fmt::format;
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = 1;
            println!("{}", a);
            print!("{}", a);
            format!("{}", a);
            panic!("{}", a)
        }
    """)


    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test writeln macro argument`() = checkWarnings("""
        use std::fmt::Write as _;

        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = "test";
            let mut w = String::new();
            writeln!(w, "result: {}", a).unwrap();
        }
    """)


    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test write macro argument`() = checkWarnings("""
        use std::fmt::Write as _;

        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = "test";
            let mut w = String::new();
            write!(w, "result: {}", a).unwrap();
        }
    """)


    fun `test assert macro argument`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = true;
            assert!(a);
            assert_eq!(a, true);
            assert_ne!(a, false);
            debug_assert!(a);
        }
    """)

    fun `test vec macro argument`() = checkWarnings("""
        use std::fmt::format;
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = 1;
            let mut b = vec![a];
            b = vec![];
        }
    """)

    fun `test expr macro argument`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = 1;
            dbg!(a);

            let mut b = 2;
            dbg!(b = 10);
        }
    """)

    fun `test concat macro argument`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = 1;
            concat!(a, "5");
        }
    """)

    fun `test env macro argument`() = checkWarnings("""
        fn main() {
            let /*weak_warning descr="Unused `mut`"*/mut/*weak_warning**/ a = "PATH";
            env!(a);
        }
    """)

    fun `test asm macro argument`() = checkWarnings(
        """
        use std::arch::asm;
        fn main() {
            let mut a: u64 = 4;
            unsafe {
                asm!(
                    "add {a}, 2",
                    a = inout(reg) a,
                );
            }
            assert_eq!(a, 6);
        }
    """
    )

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


    fun `test should not annotate allowed unused variable`() = checkWarnings("""
        #[allow(unused_mut)]
        fn main() {
            let mut f = 10;
        }
    """)

    fun `test use error highlighting with deny unused_mut`() = checkWarnings("""
        #[deny(unused_mut)]
        fn main() {
            let /*error descr="Unused `mut`"*/mut/*error**/ f = 10;
        }
    """)
}
