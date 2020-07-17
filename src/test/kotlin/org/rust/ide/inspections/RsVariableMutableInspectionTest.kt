/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ExpandMacros

class RsVariableMutableInspectionTest : RsInspectionsTestBase(RsVariableMutableInspection::class) {
    fun `test dead variable without init`() = checkByText("""
        fn foo() {
            let <warning descr="Variable `x` does not need to be mutable">mut</warning> x: i32;
        }
    """)

    fun `test dead variable with init`() = checkByText("""
        fn main() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = 10;
        }
    """)

    fun `test dead variable without mut`() = checkByText("""
        fn main() {
            let f = 10;
        }
    """)

    fun `test immutable ref`() = checkByText("""
        fn main() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = 10;
            let _ = &f;
        }
    """)

    fun `test mutable ref`() = checkByText("""
        fn main() {
            let mut f = 10;
            let _ = &mut f;
        }
    """)

    fun `test self method`() = checkByText("""
        struct Foo { foo: bool }
        impl Foo {
            fn push(&self, test: i32) {}
        }
        fn foo() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = Foo { foo: true };
            f.push(t);
        }
    """)

    fun `test mut self method`() = checkByText("""
        struct Foo { foo: bool }
        impl Foo {
            fn push(&mut self, test: i32) {}
        }
        fn foo() {
            let mut f = Foo { foo: true };
            f.push(t);
        }
    """)

    fun `test self method with UFCS`() = checkByText("""
        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }
        fn foo() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = Foo;
            Foo::bar(&f);
        }
    """)

    fun `test mut self method with UFCS`() = checkByText("""
        struct Foo;
        impl Foo {
            fn bar(&mut self) {}
        }
        fn foo() {
            let mut f = Foo;
            Foo::bar(&mut f);
        }
    """)

    fun `test immutable field reference`() = checkByText("""
        struct Foo { foo: bool }
        fn foo(b: &bool) {}
        fn bar() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = Foo { foo: true };
            foo(&f.foo);
        }
    """)

    fun `test mutable field reference`() = checkByText("""
        struct Foo { foo: bool }
        fn foo(b: &mut bool) {}
        fn bar() {
            let mut f = Foo { foo: true };
            foo(&mut f.foo);
        }
    """)

    fun `test mutable nested field reference`() = checkByText("""
        struct Bar { bar: bool }
        struct Foo { foo: bool, bar: Bar }

        fn foo(b: &mut bool) {}

        fn bar() {
            let mut f = Foo { foo: true, bar: Bar { bar: true } };
            foo(&mut f.bar.bar);
        }
    """)

    fun `test moved variable`() = checkByText("""
        fn foo(i: i32) {
            println!("{:?}", i);
        }

        fn main() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = 10;
            foo(f);
        }
    """)

    fun `test mutated parameter`() = checkByText("""
        fn foo(i: &mut i32) {
            *i = 20;
        }

        fn main() {
            let mut f = 10;
            foo(&mut f);
        }
    """)

    fun `test immutable dereference 1`() = checkByText("""
        fn main() {
            let <warning descr="Variable `a` does not need to be mutable">mut</warning> a = &10;
            let _ = *b;
        }
    """)

    fun `test immutable dereference 2`() = checkByText("""
        fn foo(a: &u32) -> &'static mut u32 { unreachable!() }
        fn main() {
            let <warning descr="Variable `a` does not need to be mutable">mut</warning> a = &10;
            *foo(&a) = 5;
        }
    """)

    fun `test mutable dereference`() = checkByText("""
        fn main() {
            let mut a = 0;
            let <warning descr="Variable `b` does not need to be mutable">mut</warning> b = &mut a;
            *b = 5;
        }
    """)

    fun `test redeclaration`() = checkByText("""
        fn main() {
            let <warning descr="Variable `f` does not need to be mutable">mut</warning> f = 10;
            let f = 10;
        }
    """)

    fun `test assignment after init`() = checkByText("""
        fn main() {
            let mut a = 10;
            a = 5;
        }
    """)

    fun `test tuple`() = checkByText("""
        fn bar(s: &mut (u8, u8)) {}

        fn main() {
            let <warning descr="Variable `a` does not need to be mutable">mut</warning> a = 1u8;
            let <warning descr="Variable `b` does not need to be mutable">mut</warning> b = 2u8;
            bar(&mut (a, b));
        }
    """)

    fun `test ref mut binding`() = checkByText("""
        fn main() {
            let mut a = 10;
            let ref mut b = a;
        }
    """)

    fun `test if binding`() = checkByText("""
        fn main() {
            let mut a = Some(20);
            if let Some(ref mut b) = a {
                *b = 10;
            }
        }
    """)

    fun `test match binding`() = checkByText("""
        fn main() {
            let mut a = Some(20);
             match a {
                Some(ref mut b) => *b = 10,
                None => {},
            }
        }
    """)

    @ExpandMacros
    fun `test should not annotate if used within macro`() = checkByText("""
        macro_rules! use_var {
            (${'$'}x:expr) => { ${'$'}x = 20; }
        }

        fn main() {
            let mut w = 10;
            use_var!(w);
        }
    """)

    @ExpandMacros
    fun `test should annotate if not mutated within macro`() = checkByText("""
        macro_rules! use_var { () => { w; } }

        fn main() {
            let <warning descr="Variable `w` does not need to be mutable">mut</warning> w = 10;
            use_var!();
        }
    """)

    fun `test assigned function parameter fix `() = checkFixByText("Remove mut", """
        fn foo(mut test: i32) {
            test = 10;
        }
        fn foo2(<warning descr="Parameter `test` does not need to be mutable">/*caret*/mut</warning> test: i32) {
        }
    """, """
        fn foo(mut test: i32) {
            test = 10;
        }
        fn foo2(test: i32) {
        }
    """)

    fun `test redeclaration fix`() = checkFixByText("Remove mut", """
        fn main() {
            let mut f = 10;
            f = 20;
            let <warning descr="Variable `f` does not need to be mutable">/*caret*/mut</warning> f = 10;
        }
    """, """
        fn main() {
            let mut f = 10;
            f = 20;
            let f = 10;
        }
    """)

    // TODO: make check more precise to only spot FnMut lambdas
    fun `test mutable lambda call`() = checkByText("""
        fn main() {
            let mut a = 10;
            let mut foo = || a = 20;
            foo();
        }
    """)

    fun `test requires mutable only in one path`() = checkByText("""
        fn main() {
            let mut a = 10;
            if true {
                let _ = &a;
            } else {
                let _ = &mut a;
            }
        }
    """)

    fun `test binary operation`() = checkByText("""
        fn main() {
            let <warning descr="Variable `a` does not need to be mutable">mut</warning> a = 10;
            let _ = a + 1;
        }
    """)

    /*fun `test late initialization`() = checkByText("""
        fn foo() {
            let <warning descr="Variable `x` does not need to be mutable">mut</warning> x: i32;
            x = 1;
        }
    """)

    fun `test single initialization in multiple paths`() = checkByText("""
        fn foo() {
            let <warning descr="Variable `x` does not need to be mutable">mut</warning> x: i32;
            if true {
                x = 1;
            } else {
                x = 2;
            }
        }
    """)*/
}
