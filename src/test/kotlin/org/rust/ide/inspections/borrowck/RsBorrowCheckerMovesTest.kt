/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase

class RsBorrowCheckerMovesTest : RsInspectionsTestBase(RsBorrowCheckerInspection::class) {
    fun `test move by call`() = checkByText("""
        struct S { data: i32 }

        fn f(s: S) {}

        fn main() {
            let x = S { data: 42 };
            let mut i = 0;
            while i < 10 {
                if x.data == 10 { f(<error descr="Use of moved value">x</error>); } else {}
                i += 1;
            }
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move struct by assign 1`() = checkByText("""
        struct S;

        fn main() {
            let x = S;
            let y = x;
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move struct by assign 2`() = checkByText("""
        struct S;

        fn main() {
            let x = S;
            let mut y = 2;
            y = x;
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move struct by assign 3`() = checkByText("""
        #[derive(Default)]
        struct S { a: i32, b: i32, }

        fn main() {
            let mut s = S { a: 1, ..Default::default() };
            s.b = 2;
            let s1 = s;
            <error descr="Use of moved value">s</error>;
        }
    """, checkWarn = false)

    fun `test move enum by assign 1`() = checkByText("""
        enum E { One }

        fn main() {
            let x = E::One;
            let y = x;
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move enum by assign 2`() = checkByText("""
        struct S;
        enum E { One, Two(S) }

        fn main() {
            let x = E::One;
            let y = x;
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move enum by assign 3`() = checkByText("""
        struct S;
        enum E { One, Two { x: S } }

        fn main() {
            let x = E::One;
            let y = x;
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move field`() = checkByText("""
        struct T;
        struct S { data: T }

        fn main() {
            let x = S { data: T };
            x.data;
            <error descr="Use of moved value">x.data</error>;
        }
    """, checkWarn = false)

    fun `test move other field`() = checkByText("""
        struct T;
        struct S { data1: T, data2: T }

        fn main() {
            let x = S { data1: T, data2: T };
            x.data1;
            x.data2;
        }
    """, checkWarn = false)

    fun `test move from raw pointer`() = checkByText("""
        struct S;

        unsafe fn foo(x: *const S) -> S {
            let y;
            y = <error descr="Cannot move">*x</error>;
            return y;
        }

        fn main() {}
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move from array`() = checkByText("""
        struct S;

        fn main() {
            let arr: [S; 1] = [S];
            let x = <error descr="Cannot move">arr[0]</error>;
        }
    """, checkWarn = false)

    fun `test move in struct literal`() = checkByText("""
        struct S;
        struct T { s1: S, s2: S }
        fn main() {
            let s = S;
            let t = T { s1: s, s2: <error descr="Use of moved value">s</error> };
        }
    """, checkWarn = false)

    fun `test move in shorthand struct literal`() = checkByText("""
        struct S;
        struct T { s: S }
        fn main() {
            let s = S;
            s;
            let t = T { <error descr="Use of moved value">s</error> };
        }
    """, checkWarn = false)

    fun `test move else if`() = checkByText("""
        struct S;
        
        fn main() {
            let x = S;
            if a {
            } else if b {
                x;
            }
            <error descr="Use of moved value">x</error>;
        }
    """, checkWarn = false)

    fun `test move for loop`() = checkByText("""
        struct S { data: i32 }
        struct T;

        fn f(s: S) {}

        fn main() {
            let x = S { data: 42 };
            for mut i in 0..5 {
                if x.data == 10 { f(<error descr="Use of moved value">x</error>); } else {}
                i += 1;
            }
            <error descr="Use of moved value">x<caret></error>;

            let ts = vec![T, T, T];
            for t in ts { t; }
        }
    """, checkWarn = false)

    fun `test move in while let or patterns`() = checkByText("""
        struct S;
        enum E { A(S), B(S), C }

        fn foo(e: E) {
            if let E::A(s) | E::B(s) = e {}
            while let <error descr="Use of moved value">E::A(s)</error> | <error descr="Use of moved value">E::B(s)</error> = e {}
        }
    """, checkWarn = false)

    fun `test no move error E0382 when matching path`() = checkByText("""
        enum Kind { A, B }
        pub struct DeadlineError(Kind);

        impl DeadlineError {
            fn f(&self) {
                use self::Kind::*;
                match self.0 { A => {} };
                match self.0 { Kind::B => {} };
            }
        }
    """, checkWarn = false)

    fun `test no move error E0382 if let or patterns`() = checkByText("""
        struct S;
        enum E { A(S), B(S), C }

        fn foo(e: E) {
            if let E::A(s) | E::B(s) = e {
                s;
            }
        }
    """, checkWarn = false)

    fun `test no move error E0382 when closure used twice`() = checkByText("""
        fn main() {
            let f = |x: i32| {};
            f(1);
            f(2);
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 on binary expr as method call`() = checkByText("""
        #[derive (PartialEq)]
        struct S { data: i32 }

        fn main() {
            let x = S { data: 42 };
            let y = S { data: 1 };
            if x == y {
                x;
            }
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 on ref String and &str`() = checkByText("""
        use std::string::String;
        enum E { A(String), B }
        fn main() {
            let x = E::A(String::from("abc"));
            match x {
                E::A(ref s) if *s == "abc" => {}
                _ => {}
            }
        }
    """, checkWarn = false)

    fun `test no move error E0382 on method call`() = checkByText("""
        struct S { }
        impl S {
            fn f(&self) {}
        }

        fn main() {
            let x = S {};
            x.f();
            x;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 on field getter`() = checkByText("""
        struct S {
            data: (u16, u16, u16)
        }
        impl S {
            fn get_data(&self) -> (u16, u16, u16) { self.data }
        }

        fn main() {
            let x = S { data: (1, 2, 3) };
            x.get_data();
            x;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 when let in while`() = checkByText("""
        struct S { a: i32 }

        fn main() {
            let mut xs: Vec<S>;
            while let Some(x) = xs.pop() {
                f(x);
            }
        }

        fn f(node: S) {}
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 when reference to type parameter`() = checkByText("""
        fn foo<T>(x: &T) {
            let y = x;
            x;
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 when mut reference to self`() = checkByText("""
        struct T;

        fn bar(t: &mut T) {}

        struct S<'a> { a: &'a mut T }
        impl<'a> S<'a> {
            fn foo(&mut self) {
                bar(self.a)
            }
        }
    """, checkWarn = false)

    /** Issue [#3040](https://github.com/intellij-rust/intellij-rust/issues/3040) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0507 when deref Box`() = checkByText("""
        fn main() {
            struct S;
            let a = *Box::new(S);
            let (b, c) = *Box::new((S, S));
        }
    """, checkWarn = false)

    /** Issue [#3062](https://github.com/intellij-rust/intellij-rust/issues/3062) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0507 when deref copyable self`() = checkByText("""
        trait Dummy where Self: Copy {
            fn dummy(&self) -> Self {
                *self
            }
        }
    """, checkWarn = false)

    /** Issue [#3093](https://github.com/intellij-rust/intellij-rust/issues/3093) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0507 on copyable type parameter`() = checkByText("""
        fn foo<X: Copy>(x: &[X]) -> X {
            x[0]
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0507 on copyable array`() = checkByText("""
        fn copy(arr: &[i32; 4]) -> [i32; 4] {
            *arr
        }
    """, checkWarn = false)

    /** Issue [#3251](https://github.com/intellij-rust/intellij-rust/issues/3251) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 after noreturn`() = checkByText("""
        fn noreturn() -> ! { panic!() }

        struct S;
        fn consume(s: S) {}

        fn main() {
            let s = S;
            if true {
                consume(s);
                noreturn();
            }
            consume(s);
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 after expr stmt with never type`() = checkByText("""
        struct S;
        fn main() {
            let s = S;
            let s1 = s;
            panic!();
            let s2 = s;
        }
    """, checkWarn = false)

    /** Issue [#3270](https://github.com/intellij-rust/intellij-rust/issues/3270) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error E0382 if let`() = checkByText("""
        struct S;

        fn main() {
            let x: Option<S> = Some(S);

            if let Some(s) = x {
                Some(s)
            } else {
                x
            };
        }
    """, checkWarn = false)

    /** Issue [#3314](https://github.com/intellij-rust/intellij-rust/issues/3314) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error deref str inside binary expr`() = checkByText("""
        fn main() {
            let a = "abc";
            let b = "abc";
            if *a == *b {}
        }
    """, checkWarn = false)

    fun `test no move error destruct array`() = checkByText("""
        struct S;

        fn main() {
            let arr = [S, S, S];
            let [a, b, c] = arr;
        }
    """, checkWarn = false)

    fun `test move array after destruct`() = checkByText("""
        struct S;

        fn main() {
            let arr = [S, S, S];
            let [a, b, c] = arr;
            <error descr="Use of moved value">arr</error>;
        }
    """, checkWarn = false)

    fun `test double destruct array`() = checkByText("""
        struct S;

        fn main() {
            let arr = [S, S, S];
            let [a, b, c] = arr;
            let <error descr="Use of moved value">[a1, b1, c1]</error> = arr;
        }
    """, checkWarn = false)

    fun `test move into struct pat`() = checkByText("""
        struct S;
        struct T { x: S }

        fn main() {
            let t = T { x: S };
            let T { x: x } = t;
            <error descr="Use of moved value">t</error>;
        }
    """, checkWarn = false)

    fun `test move into struct pat shorthand`() = checkByText("""
        struct S;
        struct T { x: S }

        fn main() {
            let t = T { x: S };
            let T { x } = t;
            <error descr="Use of moved value">t</error>;
        }
    """, checkWarn = false)

    fun `test move inside struct pat`() = checkByText("""
        struct S;
        struct T { s: S }

        fn main() {
            let s = S;
            let t1 = T { s: s };
            let t2 = T { s: <error descr="Use of moved value">s</error> };
        }
    """, checkWarn = false)

    /** Issue [#3970](https://github.com/intellij-rust/intellij-rust/issues/3970) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error while let with break`() = checkByText("""
        struct S;
        fn main() {
            let mut a = Some(S);
            while let Some(b) = a {
                match false {
                    true => { a = Some(S) }
                    _ => break
                }
            }
        }
    """, checkWarn = false)

    fun `test no move error after break 1`() = checkByText("""
        struct S;
        fn main() {
            let s = S;
            loop {
                s;
                break;
            }
        }
    """, checkWarn = false)

    fun `test no move error after break 2`() = checkByText("""
        struct S;
        fn main() {
            loop {
                let s = S;
                if cond {
                    s;
                    break;
                }
                s;
            }
        }
    """, checkWarn = false)

    fun `test no move error after break nested`() = checkByText("""
        struct S;
        fn main() {
            let s = S;
            'outer: loop {
                s;
                loop {
                    break 'outer;
                }
            }
        }
    """, checkWarn = false)

    fun `test no move error after continue`() = checkByText("""
        struct S;
        fn main() {
            loop {
                let s = S;
                if cond {
                    s;
                    continue;
                }
                s;
            }
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move from non-copy slice`() = checkByText("""
        struct S;
        fn main() {
            let v: Vec<S> = vec![S, S, S];
            <error descr="Cannot move">if let [a, b, c] = v[..] {}</error>;
        }
    """, checkWarn = false)

    /** Issue [#4307](https://github.com/intellij-rust/intellij-rust/issues/4307) */
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error copy slice`() = checkByText("""
        fn main() {
            let v: Vec<i32> = vec![1, 2, 3];
            if let [a, b, c] = v[..] {}
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error for loop`() = checkByText("""
        struct S;
        fn main() {
            let xs: Vec<S> = vec![S, S, S];
            for x in xs {
                let y = x;
            }
        }
    """, checkWarn = false)

    fun `test move in lambda expr`() = checkByText("""
        struct S;
        fn main() {
            let s = S;
            let f = || { s };
            <error descr="Use of moved value">s</error>;
        }
    """, checkWarn = false)

    fun `test move in paren expr`() = checkByText("""
        struct S;
        fn main() {
            let s = S;
            (s);
            <error descr="Use of moved value">s</error>;
        }
    """, checkWarn = false)

    fun `test move in expanded macro call`() = checkByText("""
        macro_rules! my_macro_move {
            ($ e:expr) => ($ e;);
        }
        struct S;
        fn main() {
            let s = S;
            my_macro_move!(s);
            <error descr="Use of moved value">s</error>;
        }
    """, checkWarn = false)

    /* Issue https://github.com/intellij-rust/intellij-rust/issues/4795 */
    fun `test no move when deref raw pointer`() = checkByText("""
        struct Node<T> {
            data: T,
            next: *mut Self,
        }
        
        impl<T> Node<T> {
            unsafe fn next(node: *mut Self) -> *mut Self {
                (*node).next
            }
        
            unsafe fn set_next(node: *mut Self, next: *mut Self) {
                (*node).next = next;
            }
        }
    """, checkWarn = false)

    fun `test infinitely recursive macro call`() = checkByText("""
        macro_rules! infinite_macro {
            ($ e:expr) => { infinite_macro!($ e) };
        }
        
        struct S;
        
        fn main() {
            let x = S;
            infinite_macro!(x);
        }
    """, checkWarn = false)
}
