/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ProjectDescriptor
import org.rust.WithExperimentalFeatures
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.experiments.RsExperiments.MIR_BORROW_CHECK
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

@WithExperimentalFeatures(MIR_BORROW_CHECK)
class RsBorrowCheckerUninitializedTest : RsInspectionsTestBase(RsBorrowCheckerInspection::class) {
    fun `test E0381 error no init`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let x: i32;
            <error descr="Use of possibly uninitialized variable [E0381]">x<caret></error>;
        }
    """, """
        fn main() {
            let x: i32 = 0;
            x;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test E0381 error no init default impl`() = checkFixByText("Initialize with a default value", """
        #[derive(Default)]
        struct A {
            a: u64
        }

        fn main() {
            let x: A;
            <error descr="Use of possibly uninitialized variable [E0381]">x<caret></error>;
        }
    """, """
        #[derive(Default)]
        struct A {
            a: u64
        }

        fn main() {
            let x: A = Default::default();
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error no init default initialization`() = checkFixByText("Initialize with a default value", """
        struct A {
            a: u64,
            b: u64
        }

        fn main() {
            let a: u64 = 1;
            let x: A;
            <error descr="Use of possibly uninitialized variable [E0381]">x<caret></error>;
        }
    """, """
        struct A {
            a: u64,
            b: u64
        }

        fn main() {
            let a: u64 = 1;
            let x: A = A { a, b: 0 };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside then`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let x: i32;
            if something { x = 1 } else {};
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, """
        fn main() {
            let x: i32 = 0;
            if something { x = 1 } else {};
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside then mutable`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let mut x: i32;
            if something { x = 1 } else {};
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, """
        fn main() {
            let mut x: i32 = 0;
            if something { x = 1 } else {};
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error fix unavailable tuple 1`() = checkFixIsUnavailable("Initialize with a default value", """
        fn main() {
            let (x,): (i32,);
            if something { x = 1 } else {};
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, checkWarn = false)

    fun `test E0381 error fix unavailable tuple 2`() = checkFixIsUnavailable("Initialize with a default value", """
        fn main() {
            let (x, y): (i32, i32);
            if something { x = 1 } else {};
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside else`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let x: i32;
            if something {} else { x = 1 };
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, """
        fn main() {
            let x: i32 = 0;
            if something {} else { x = 1 };
            x;
        }
    """, checkWarn = false)

    fun `test no E0381 error init inside then and else`() = checkByText("""
        fn main() {
            let x: i32;
            if something { x = 1 } else { x = 2 };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside match arm`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let x: i32;
            match 42 {
                0...10 => { x = 1 }
                _ => {}
            };
            <error descr="Use of possibly uninitialized variable">x<caret></error>;
        }
    """, """
        fn main() {
            let x: i32 = 0;
            match 42 {
                0...10 => { x = 1 }
                _ => {}
            };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error no explicit type`() = checkFixByText("Initialize with a default value", """
        fn main() {
            let x;
            let y: i32 =  /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*caret*//*error**/;
        }
    """, """
        fn main() {
            let x = 0;
            let y: i32 =  x;
        }
    """, checkWarn = false)

    fun `test E0381 error declaration with attribute`() = checkFixByText("Initialize with a default value", """
        fn main() {
            #[foobar]
            let x;
            let y: i32 =  /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*caret*//*error**/;
        }
    """, """
        fn main() {
            #[foobar]
            let x = 0;
            let y: i32 =  x;
        }
    """, checkWarn = false)

    fun `test E0381 error declaration with comments`() = checkFixByText("Initialize with a default value", """
        fn main() {
            // 123
            let x; // 321
            let y: i32 =  /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*caret*//*error**/;
        }
    """, """
        fn main() {
            // 123
            let x = 0; // 321
            let y: i32 =  x;
        }
    """, checkWarn = false)

    fun `test no E0381 error init inside all match arms`() = checkByText("""
        fn main() {
            let x: i32;
            match 42 {
                0...10 => { x = 1 }
                _ => { x = 2 }
            };
            x;
        }
    """, checkWarn = false)

    fun `test E0381 error init inside while`() = checkByText("""
        fn main() {
            let x: i32;
            while something {
                x = 1;
            };
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
    """, checkWarn = false)

    /** Issue [#4008](https://github.com/intellij-rust/intellij-rust/issues/4008) */
    fun `test no E0381 never type`() = checkByText("""
        fn foo(flag: bool) -> i32 {
            let value: i32;
            match flag {
                true => { value = 1 }
                false => panic!()
            };
            value
        }
    """, checkWarn = false)

    fun `test no E0381 init in macro call`() = checkByText("""
        macro_rules! my_macro_init {
            ($ i:ident) => ($ i = 42);
        }
        fn main() {
            let value: i32;
            my_macro_init!(value);
            value;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no E0381 asm! macro`() = checkByText("""
        #![feature(asm)]

        fn main() {
            let x: u64;
            unsafe {
                asm!("mov {}, 5", out(reg) x);
            }
            x;
        }
    """, checkWarn = false)

    // TODO: Handle this case when type inference is implemented for `asm!` macro calls
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test E0381 asm! macro`() = expect<AssertionError> {
        checkByText("""
        #![feature(asm)]

        fn main() {
            let x: u64;
            unsafe {
                asm!("nop");
            }
            <error descr="Use of possibly uninitialized variable">x</error>;
        }
        """, checkWarn = false)
    }

    fun `test E0381 inside then branch`() = checkErrors("""
        fn main() {
            let x: i32;
            if true {
                let y = /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/;
            } else {
                x = 1;
            }
        }
    """)

    fun `test E0381 inside else branch`() = checkErrors("""
        fn main() {
            let x: i32;
            if true {
                x = 1;
            } else {
                let y = /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/;
            }
        }
    """)

    fun `test E0381 inside if expr`() = checkErrors("""
        fn main() {
            let x: i32;
            if /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/ {
                x = 1;
            } else {
                x = 2;
            }
        }
    """)

    fun `test E0381 inside loop`() = checkErrors("""
        fn main() {
            let x: i32;
            loop {
                let y = /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/;
                x = 1;
            }
        }
    """)

    fun `test no E0381 for tuple field`() = checkErrors("""
        struct Foo;
        fn main() {
            let x = (Foo, Foo);
            let y = x.0;
            let z = x.1;
        }
    """)

    fun `test no E0381 for nested tuple field`() = checkErrors("""
        struct Foo;
        fn main() {
            let x = (Foo, (Foo, Foo));
            let y1 = x.0;
            let y2 = x.1.0;
            let y3 = x.1.1;
        }
    """)

    fun `test no E0381 for struct field`() = checkErrors("""
        struct Foo { a: i32, b: i32 }
        fn main() {
            let foo = Foo { a: 1, b: 2 };
            foo.a;
            foo.b;
        }
    """)

    fun `test no E0381 for nested struct field`() = checkErrors("""
        struct Foo { a: i32 }
        struct Bar { b: i32, c: Foo }
        fn main() {
            let foo = Foo { a: 1 };
            let bar = Bar { b: 2, c: foo };
            bar.foo.a;
        }
    """)

    fun `test E0381 for add assign`() = checkErrors("""
        fn main() {
            let mut x: i32;
            /*error descr="Use of possibly uninitialized variable [E0381]"*/x += 1/*error**/;
        }
    """)

    fun `test no E0381 for add assign`() = checkErrors("""
        fn main() {
            let mut x = 0;
            x += 1;
            let mut y = 0;
            y += 1;
        }
    """)

    fun `test E0381 in function argument`() = checkErrors("""
        fn main() {
            let x: i32;
            foo(/*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/);
        }
        fn foo(_a: i32) {}
    """)

    fun `test no E0381 for variable assigned in function call`() = checkErrors("""
        fn main() {
            let mut a: i32;
            a = foo();
            let b = a;
        }
        fn foo() -> i32 { 0 }
    """)

    fun `test E0381 in struct literal`() = checkErrors("""
        struct Foo { a: i32 }
        fn main() {
            let x: i32;
            let foo = Foo { a: /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/ };
        }
    """)

    fun `test no E0381 in struct literal`() = checkErrors("""
        struct Foo { a: i32 }
        fn main() {
            let x = 1;
            let foo1 = Foo { a: x };
            let foo2 = foo1;
        }
    """)

    fun `test E0381 in tuple struct literal`() = checkErrors("""
        struct Foo(i32);
        fn main() {
            let x: i32;
            let foo = Foo(/*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/);
        }
    """)

    fun `test no E0381 in tuple struct literal`() = checkErrors("""
        struct Foo(i32);
        fn main() {
            let x = 1;
            let foo1 = Foo(x);
            let foo2 = foo1;
        }
    """)

    fun `test E0381 in enum variant literal`() = checkErrors("""
        enum E { Foo { a: i32 } }
        fn main() {
            let x: i32;
            let foo = E::Foo { a: /*error descr="Use of possibly uninitialized variable [E0381]"*/x/*error**/ };
        }
    """)

    fun `test no E0381 in enum variant literal`() = checkErrors("""
        enum E { Foo { a: i32 } }
        fn main() {
            let x = 1;
            let foo1 = E::Foo { a: x };
            let foo2 = foo1;
        }
    """)

    fun `test no E0381 in builtin operators`() = checkErrors("""
        fn main() {
            let x = 1;
            let y = 2;
            x + y; x - y; x * y; x / y; x % y;
            x & y; x | y; x ^ y; x << y; x >> y;
            x > y; x >= y; x < y; x <= y;
            x == y; x != y;
            x; y;
        }
    """)

    fun `test no E0381 in match pat binding`() = checkErrors("""
        fn main() {
            let e = 1;
            let e3 = match e {
                e2 => e2,
            };
            e3;
        }
    """)

    fun `test no E0381 in match enum without fields`() = checkErrors("""
        enum E { A, B }
        fn main() {
            let e = E::A;
            let x = match e {
                E::A => { 1 }
                E::B => { 2 }
            };
            x;
            e;
        }
    """)

    fun `test no E0381 in match enum with fields`() = checkErrors("""
        enum E { A(i32), B(i32, i32, i32, i32, i32) }
        fn main() {
            let e = E::A(0);
            let x = match e {
                E::A(a) => { a }
                E::B(b1, b2, .., b3) => { b1 + b2 + b3 }
            };
            x;
            e;
        }
    """)

    fun `test no E0381 for path expr reference to named const`() = checkErrors("""
        const C: i32 = 1;
        fn main() {
            let x = C;
            x;
        }
    """)
}
