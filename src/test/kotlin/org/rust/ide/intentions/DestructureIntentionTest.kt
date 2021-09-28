/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.intentions

class DestructureIntentionTest : RsIntentionTestBase(DestructureIntention::class) {
    fun `test destructure variable`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() { let /*caret*/x = S { x: 0, y: "" }; }
    """, """
        struct S<T, U> { x: T, y: U }
        fn main() { let /*caret*/S { x, y } = S { x: 0, y: "" }; }
    """)

    fun `test destructure block struct replace fields`() = doAvailableTest("""
        struct P<T> { p: T }
        struct S<T, U> { x: P<T>, y: U }
        fn main() {
            let /*caret*/s = S { x: P { p: 0 }, y: "" };
            let z = s.x.p;
        }
    """, """
        struct P<T> { p: T }
        struct S<T, U> { x: P<T>, y: U }
        fn main() {
            let /*caret*/S { x, y } = S { x: P { p: 0 }, y: "" };
            let z = x.p;
        }
    """)

    fun `test destructure block struct replace direct usage`() = doAvailableTest("""
        struct S { x: u32, y: u32 }

        fn foo(s: S) {}
        fn main() {
            let /*caret*/s = S { x: 0, y: 0 };
            foo(s);
        }
    """, """
        struct S { x: u32, y: u32 }

        fn foo(s: S) {}
        fn main() {
            let /*caret*/S { x, y } = S { x: 0, y: 0 };
            foo(S { x, y });
        }
    """)

    fun `test destructure block struct ignore method call`() = doAvailableTest("""
        struct S { x: u32, y: u32 }
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let /*caret*/s = S { x: 0, y: 0 };
            s.foo();
        }
    """, """
        struct S { x: u32, y: u32 }
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let /*caret*/S { x, y } = S { x: 0, y: 0 };
            s.foo();
        }
    """)

    fun `test destructure tuple struct replace fields`() = doAvailableTest("""
        struct A { a: u32 }
        struct S(A, u32);

        fn main() {
            let /*caret*/s = S(A { a: 0 }, 0);
            let z = s.0.a;
        }
    """, """
        struct A { a: u32 }
        struct S(A, u32);

        fn main() {
            let S(_0, _1) = S(A { a: 0 }, 0);
            let z = _0.a;
        }
    """)

    fun `test destructure tuple struct replace direct usage`() = doAvailableTest("""
        struct S(u32, u32);

        fn foo(s: S) {}
        fn main() {
            let /*caret*/s = S(0, 0);
            foo(s);
        }
    """, """
        struct S(u32, u32);

        fn foo(s: S) {}
        fn main() {
            let S(_0, _1) = S(0, 0);
            foo(S(_0, _1));
        }
    """)

    fun `test destructure tuple element method call`() = doAvailableTest("""
        struct S;
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let /*caret*/s = (S, S);
            s.0.foo();
        }
    """, """
        struct S;
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let (_0, _1) = (S, S);
            _0.foo();
        }
    """)

    fun `test destructure tuple struct ignore method call`() = doAvailableTest("""
        struct S(u32, u32);
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let /*caret*/s = S(0, 0);
            s.foo();
        }
    """, """
        struct S(u32, u32);
        impl S {
            fn foo(&self) {}
        }

        fn main() {
            let S(_0, _1) = S(0, 0);
            s.foo();
        }
    """)

    fun `test destructure tuple replace fields`() = doAvailableTest("""
        fn main() {
            let /*caret*/s = (1, 2);
            let z = s.0;
            let y = s.1;
        }
    """, """
        fn main() {
            let (_0, _1) = (1, 2);
            let z = _0;
            let y = _1;
        }
    """)

    fun `test destructure tuple replace nested named fields`() = doAvailableTest("""
        struct S { a: u32, b: u32}

        fn main() {
            let /*caret*/s = (S { a: 0, b: 0 }, S { a: 0, b: 0 });
            let z = s.0.a;
            let y = s.1.b;
        }
    """, """
        struct S { a: u32, b: u32}

        fn main() {
            let (_0, _1) = (S { a: 0, b: 0 }, S { a: 0, b: 0 });
            let z = _0.a;
            let y = _1.b;
        }
    """)

    fun `test destructure tuple replace nested numeric fields`() = doAvailableTest("""
        fn main() {
            let /*caret*/s = ((0, 0), (0, 0));
            let z = (s.0).0;
            let y = (s.1).1;
        }
    """, """
        fn main() {
            let (_0, _1) = ((0, 0), (0, 0));
            let z = (_0).0;
            let y = (_1).1;
        }
    """)

    fun `test destructure tuple replace direct usage`() = doAvailableTest("""
        fn foo(_: (i32, i32)) {}
        fn main() {
            let /*caret*/s = (1, 2);
            foo(s);
        }
    """, """
        fn foo(_: (i32, i32)) {}
        fn main() {
            let (_0, _1) = (1, 2);
            foo((_0, _1));
        }
    """)

    fun `test destructure tuple replace fields with template`() = doAvailableTestWithLiveTemplate("""
        fn main() {
            let /*caret*/s = (1, 2);
            let x = s.0;
            let y = s.1;
            let z = (s.0, s.1);
        }
    """, "a\tb\t", """
        fn main() {
            let (a, b) = (1, 2);
            let x = a;
            let y = b;
            let z = (a, b);
        }
    """)

    fun `test destructure parameter`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn f(/*caret*/x: S<i32, &str>) {}
    """, """
        struct S<T, U> { x: T, y: U }
        fn f(/*caret*/S { x, y }: S<i32, &str>) {}
    """)

    fun `test destructure match arm`() = doAvailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() {
            match (S { x: 0, y: "" }) {
               /*caret*/x => ()
            }
        }
    """, """
        struct S<T, U> { x: T, y: U }
        fn main() {
            match (S { x: 0, y: "" }) {
               /*caret*/S { x, y } => ()
            }
        }
    """)

    fun `test tuple struct`() = doAvailableTest("""
        struct S(i32, i32);
        fn f(/*caret*/x: S) {}
    """, """
        struct S(i32, i32);
        fn f(S(_0, _1): S) {}
    """)

    fun `test tuple`() = doAvailableTest("""
        fn f(/*caret*/x: (i32, u128)) {}
    """, """
        fn f((_0, _1): (i32, u128)) {}
    """)

    fun `test unit struct`() = doAvailableTest("""
        struct S;
        fn f(/*caret*/x: S) {}
    """, """
        struct S;
        fn f(S {}: S) {}
    """)

    fun `test struct with no params`() = doAvailableTest("""
        struct S {}
        fn f(/*caret*/x: S) {}
    """, """
        struct S {}
        fn f(S {}: S) {}
    """)

    fun `test tuple struct with no params`() = doAvailableTest("""
        struct S();
        fn f(/*caret*/x: S) {}
    """, """
        struct S();
        fn f(S(): S) {}
    """)

    fun `test union`() = doAvailableTest("""
        union U { x: i32, y: u128 }
        fn f(/*caret*/x: U) {}
    """, """
        union U { x: i32, y: u128 }
        fn f(U { x, y }: U) {}
    """)

    fun `test unavailable for enum`() = doUnavailableTest("""
        enum S { A, B }
        fn f(/*caret*/x: S) {}
    """)

    fun `test unavailable for unknown type`() = doUnavailableTest("""
        fn f(/*caret*/x: S) {}
    """)

    fun `test unavailable if no pat ident`() = doUnavailableTest("""
        struct S<T, U> { x: T, y: U }
        fn main() { let S { /*caret*/x, y } = S { x: S { x: 1, y: 2 }, y: "" }; }
    """)

    fun `test unavailable if private fields`() = doUnavailableTest("""
        mod a {
            pub struct S { pub x: i32, y: i32 }
        }

        fn main() {
            use crate::a::S;
            let /*caret*/x = S { x: 0, y: 1 };
        }
    """)

    fun `test import unresolved type`() = doAvailableTest("""
        use a::foo;

        mod a {
            pub struct S;
            pub fn foo() -> S { S }
        }

        fn main() {
            let /*caret*/x = foo();
        }
    """, """
        use a::{foo, S};

        mod a {
            pub struct S;
            pub fn foo() -> S { S }
        }

        fn main() {
            let S {} = foo();
        }
    """)

    fun `test import unresolved type alias`() = doAvailableTest("""
        use a::foo;

        mod a {
            pub struct S;
            pub type R = S;
            pub fn foo() -> R { S }
        }

        fn main() {
            let /*caret*/x = foo();
        }
    """, """
        use a::{foo, R};

        mod a {
            pub struct S;
            pub type R = S;
            pub fn foo() -> R { S }
        }

        fn main() {
            let R {} = foo();
        }
    """)

    fun `test ignore existing binding name`() = doAvailableTest("""
        fn main() {
            let /*caret*/_0 = (0, 1);
            let a = _0.0;
            let b = _0.1;
        }
    """, """
        fn main() {
            let (_0, _1) = (0, 1);
            let a = _0;
            let b = _1;
        }
    """)

    fun `test skip existing bindings tuple`() = doAvailableTest("""
        fn main() {
            let /*caret*/x = (0, 1);
            let _0 = 0;
            let a = x.0;
            let b = x.1;
        }
    """, """
        fn main() {
            let (_00, _1) = (0, 1);
            let _0 = 0;
            let a = _00;
            let b = _1;
        }
    """)

    fun `test skip existing bindings tuple direct usage`() = doAvailableTest("""
        fn foo(a: (u32, u32)) {}

        fn main() {
            let /*caret*/x = (0, 1);
            let _0 = 0;
            foo(x);
        }
    """, """
        fn foo(a: (u32, u32)) {}

        fn main() {
            let (_00, _1) = (0, 1);
            let _0 = 0;
            foo((_00, _1));
        }
    """)

    fun `test skip existing bindings struct tuple`() = doAvailableTest("""
        struct S(u32, u32);

        fn main() {
            let /*caret*/x = S(0, 1);
            let _0 = 0;
            let a = x.0;
            let b = x.1;
        }
    """, """
        struct S(u32, u32);

        fn main() {
            let S(_00, _1) = S(0, 1);
            let _0 = 0;
            let a = _00;
            let b = _1;
        }
    """)

    fun `test skip existing bindings struct tuple direct usage`() = doAvailableTest("""
        struct S(u32, u32);

        fn foo(s: S) {}
        fn main() {
            let /*caret*/x = S(0, 1);
            let _0 = 0;
            foo(x);
        }
    """, """
        struct S(u32, u32);

        fn foo(s: S) {}
        fn main() {
            let S(_00, _1) = S(0, 1);
            let _0 = 0;
            foo(S(_00, _1));
        }
    """)

    fun `test skip existing bindings block struct`() = doAvailableTest("""
        struct S {
            a: u32,
            b: u32
        }

        fn main() {
            let /*caret*/x = S { a: 0, b: 1 };
            let a = 0;
            let b = 1;
            let c = x.a;
            let d = x.b;
        }
    """, """
        struct S {
            a: u32,
            b: u32
        }

        fn main() {
            let S { a: a0, b: b0 } = S { a: 0, b: 1 };
            let a = 0;
            let b = 1;
            let c = a0;
            let d = b0;
        }
    """)

    fun `test skip existing bindings block struct direct usage`() = doAvailableTest("""
        struct S {
            a: u32,
            b: u32
        }

        fn foo(s: S) {}
        fn main() {
            let /*caret*/x = S { a: 0, b: 1 };
            let a = 0;
            let b = 1;
            foo(x);
        }
    """, """
        struct S {
            a: u32,
            b: u32
        }

        fn foo(s: S) {}
        fn main() {
            let S { a: a0, b: b0 } = S { a: 0, b: 1 };
            let a = 0;
            let b = 1;
            foo(S { a: a0, b: b0 });
        }
    """)

    fun `test skip existing global bindings`() = doAvailableTest("""
        struct S(u32, u32, u32, u32, u32);

        const _0: i32 = 0;
        struct _1;

        enum E { _2 }
        use E::_2;

        fn _3() {}

        fn foo(s: S) {}
        fn bar<const _4: i32>() {
            let /*caret*/x = S(0, 1, 2, 3, 4);
            foo(x);
        }
    """, """
        struct S(u32, u32, u32, u32, u32);

        const _0: i32 = 0;
        struct _1;

        enum E { _2 }
        use E::_2;

        fn _3() {}

        fn foo(s: S) {}
        fn bar<const _4: i32>() {
            let S(_00, _10, _20, _30, _40) = S(0, 1, 2, 3, 4);
            foo(S(_00, _10, _20, _30, _40));
        }
    """)
}
