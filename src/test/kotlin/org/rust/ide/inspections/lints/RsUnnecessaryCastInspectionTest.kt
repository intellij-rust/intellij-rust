/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.lints

import org.junit.ComparisonFailure
import org.junit.Test
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsInspectionsTestBase

@ProjectDescriptor(WithStdlibRustProjectDescriptor::class)// for arithmetic type inference
class RsUnnecessaryCastInspectionTest : RsInspectionsTestBase(RsUnnecessaryCastInspection::class) {

    fun `test unnecessary literal cast`() = checkWarnings("""
        fn foo() {
            let a = 1i32 /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;
            let b = 1f32 /*weak_warning descr="Unnecessary cast"*/as f32/*weak_warning**/;
            let c = false /*weak_warning descr="Unnecessary cast"*/as bool/*weak_warning**/;
            let d = true /*weak_warning descr="Unnecessary cast"*/as bool/*weak_warning**/;
            let e = 1i64 /*weak_warning descr="Unnecessary cast"*/as i64/*weak_warning**/;
            let f = 1f64 /*weak_warning descr="Unnecessary cast"*/as f64/*weak_warning**/;
            let g = 1u32 /*weak_warning descr="Unnecessary cast"*/as u32/*weak_warning**/;
            let h = 1u64 /*weak_warning descr="Unnecessary cast"*/as u64/*weak_warning**/;
            let i = 1usize /*weak_warning descr="Unnecessary cast"*/as usize/*weak_warning**/;
            let j = 1isize /*weak_warning descr="Unnecessary cast"*/as isize/*weak_warning**/;
        }
    """)

    fun `test unnecessary variable cast`() = checkWarnings("""
        fn foo() {
            let a = 1i32;
            let b = a /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;

            let c = 1f64;
            let d = c /*weak_warning descr="Unnecessary cast"*/as f64/*weak_warning**/;

            let e = 1usize;
            let f = e /*weak_warning descr="Unnecessary cast"*/as usize/*weak_warning**/;

            let g = 1;
            let h = g /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;

            let i = 1.0;
            let j = i /*weak_warning descr="Unnecessary cast"*/as f64/*weak_warning**/;
        }
    """)

    fun `test unnecessary non-trivial expression cast`() = checkWarnings("""
        fn returns_i32() -> i32 { 0 }
        fn returns_i64() -> i64 { 0 }
        fn returns_f32() -> f32 { 0.0 }
        fn returns_f64() -> f64 { 0.0 }
        fn returns_usize() -> usize { 0 }
        fn returns_isize() -> isize { 0 }

        struct S {
            v_i32: i32,
            v_i64: i64,
            v_f32: f32,
            v_f64: f64
        }

        struct Empty {}

        fn foo() {
            let a = returns_i32() /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;
            let b = returns_i64() /*weak_warning descr="Unnecessary cast"*/as i64/*weak_warning**/;
            let c = returns_f32() /*weak_warning descr="Unnecessary cast"*/as f32/*weak_warning**/;
            let d = returns_f64() /*weak_warning descr="Unnecessary cast"*/as f64/*weak_warning**/;

            let s = S { v_i32: 0, v_i64: 0, v_f32: 0.0, v_f64: 0.0 };
            let e = s.v_i32 /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;
            let f = s.v_i64 /*weak_warning descr="Unnecessary cast"*/as i64/*weak_warning**/;
            let g = s.v_f32 /*weak_warning descr="Unnecessary cast"*/as f32/*weak_warning**/;
            let h = s.v_f64 /*weak_warning descr="Unnecessary cast"*/as f64/*weak_warning**/;

            let i = returns_usize() /*weak_warning descr="Unnecessary cast"*/as usize/*weak_warning**/;
            let j = returns_isize() /*weak_warning descr="Unnecessary cast"*/as isize/*weak_warning**/;

            let k = (1 + 2 - returns_i32()) /*weak_warning descr="Unnecessary cast"*/as i32/*weak_warning**/;
            let l = &(S { v_i32: 0, v_i64: 0, v_f32: 0.0, v_f64: 0.0 }) /*weak_warning descr="Unnecessary cast"*/as &S/*weak_warning**/;

            let empty = Empty{} /*weak_warning descr="Unnecessary cast"*/as Empty/*weak_warning**/;
        }
    """)

    fun `test necessary cast`() = checkWarnings("""
        type A = i64;

        fn returns_i32() -> i32 { 0 }
        fn returns_i64() -> i64 { 0 }
        fn returns_f32() -> f32 { 0.0 }

        fn accepts_i64(a: i64) {}

        fn foo() {
            let a = 1i32;
            let b = a as i64;
            let c = a as f32;
            let d = b as f64;
            let e = a as A;
            let f = a as u32;
            let g = a as u64;

            returns_i32() as i64;
            returns_i64() as u64;
            returns_f32() as f64;

            let h = 1;
            let i = h as i32;
            accepts_i64(h);
        }
    """)

    fun `test cast in macro`() = checkWarnings("""
        macro_rules! generate_func {
            ($ a:ident, $ b:ident) => {
                pub fn $ a() -> $ b {
                    1i64 as $ b
                }
            };
        }

        macro_rules! bind_var {
            ($ id:ident, $ e:expr) => {{
                let $ id = 0usize;
                let _ = $ e != 0usize;
                let $ id = 0isize;
                let _ = $ e != 0usize;
            }}
        }

        fn foo() {
            generate_func!(a, i64);
            generate_func!(b, i32);
            bind_var!(x, (x as usize) + 1);
        }
    """)

    fun `test cast as a way to specify type`() = checkWarnings("""
        fn accepts_i32(i: i32) {}
        fn accepts_i64(i: i64) {}
        fn accepts_f32(f: f32) {}
        fn accepts_f64(f: f64) {}

        fn foo() {
            let a = 1 as i64;
            let b = 1 as i64;
            accepts_i64(b);

            let c = 1 as i32;
            let d = 1 as i32;
            accepts_i32(d);

            let e = 1.0 as f64;
            let f = 1.0 as f64;
            accepts_f64(f);

            let g = 1.0 as f32;
            let h = 1.0 as f32;
            accepts_f32(h);

            let i = 1 as i32;
            accepts_i64(i); // doesn't actually compile

            let j = 1i64;
            let k = &j as *const i64;

            let l = 1i64;
            let m = &&l as *const *const i64;
        }
    """)

    fun `test cast to usize and isize`() = checkWarnings("""
        fn foo() {
            let a = 1i16 as usize;
            let b = 1i32 as usize;
            let c = 1i64 as usize;

            let d = 1i16 as isize;
            let e = 1i32 as isize;
            let f = 1i64 as isize;

            let g = 1u16 as usize;
            let h = 1u32 as usize;
            let i = 1u64 as usize;

            let j = 1u16 as isize;
            let k = 1u32 as isize;
            let l = 1u64 as isize;
        }
    """)

    fun `test cast from usize and isize`() = checkWarnings("""
        fn foo() {
            let a = 1usize as i16;
            let b = 1usize as i32;
            let c = 1usize as i64;

            let d = 1isize as i16;
            let e = 1isize as i32;
            let f = 1isize as i64;

            let g = 1usize as u8;
            let h = 1usize as u16;
            let i = 1usize as u32;

            let j = 1isize as u8;
            let k = 1isize as u16;
            let l = 1isize as u32;
        }
    """)

    fun `test cast to unknown type`() = checkWarnings("""
        type A = i64;

        fn foo() {
            let a = 1i64 as UnknownType;
            let b = 1i64 as A;
            let c = b as UnknownType;
        }
    """)

    fun `test cast user type`() = checkWarnings("""
        type A = i64;
        type B = i64;
        type C = B;
        fn foo() {
            let a = 1i64 as A;
            let b = a as B;
            let c = b as C;
            let d = c as B;
            let e = d as A;
            let f = e as i64;

            let g = &a as *const A;
            let h = &a as &i64;

            let i = vec![1i64];
            let j = i as Vec<A>;
        }
    """)

    fun `test cast unknowns`() = checkWarnings("""
        fn foo() {
            let a = func() as T;
            let b = &func() as &T;
            let c = (&(func())) as (*T);
            let d = func() as Vec<T>;
        }
    """)

    fun `test allow`() = checkWarnings("""
        #[allow(clippy::unnecessary_cast)]
        fn foo_1() {
            let a = 1i32;
            let b = a as i32;
        }

        #[allow(clippy::complexity)]
        fn foo_2() {
            let a = 1i32;
            let b = a as i32;
        }

        #[allow(clippy::all)]
        fn foo_3() {
            let a = 1i32;
            let b = a as i32;
        }

        #[allow(clippy)]
        fn foo_4() {
            let a = 1i32;
            let b = a as i32;
        }
    """)

    fun `test remove literal cast`() = checkFixByText("Remove `as i32`", """
        fn foo() {
            let a = 1i32 /*weak_warning descr="Unnecessary cast"*/as /*caret*/i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 1i32;
        }
    """, checkWeakWarn = true)

    fun `test no fix if cast in necessary`() = checkFixIsUnavailable("Remove `as i64`", """
        fn foo() {
            let a = 1i32 as /*caret*/i64;
        }
    """, checkWeakWarn = true)


    fun `test remove variable cast`() = checkFixByText("Remove `as i32`", """
        fn foo() {
            let b = 1i32;
            let a = b /*weak_warning descr="Unnecessary cast"*/as /*caret*/i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let b = 1i32;
            let a = b;
        }
    """, checkWeakWarn = true)

    fun `test remove field access cast`() = checkFixByText("Remove `as i64`", """
        struct S {
            i: i64
        }

        fn foo() {
            let a = S { i: 1 }.i /*weak_warning descr="Unnecessary cast"*/as /*caret*/i64/*weak_warning**/;
        }
    """, """
        struct S {
            i: i64
        }

        fn foo() {
            let a = S { i: 1 }.i;
        }
    """, checkWeakWarn = true)


    fun `test remove reference cast`() = checkFixByText("Remove `as &S`", """
        struct S {
        }

        fn foo() {
            let a = &S{} /*weak_warning descr="Unnecessary cast"*/as /*caret*/&S/*weak_warning**/;
        }
    """, """
        struct S {
        }

        fn foo() {
            let a = &S{};
        }
    """, checkWeakWarn = true)

    fun `test remove function call cast`() = checkFixByText("Remove `as i32`", """
        fn returns_i32() -> i32 { 0 }
        fn foo() {
            let a = returns_i32() /*weak_warning descr="Unnecessary cast"*/as /*caret*/i32/*weak_warning**/;
        }
    """, """
        fn returns_i32() -> i32 { 0 }
        fn foo() {
            let a = returns_i32();
        }
    """, checkWeakWarn = true)

    fun `test remove cast of complex expr`() = checkFixByText("Remove `as i32`", """
        fn f() -> i32 { 0 }
        fn foo() {
            let a = (2 + 3 - f()) /*weak_warning descr="Unnecessary cast"*/as /*caret*/i32/*weak_warning**/;
        }
    """, """
        fn f() -> i32 { 0 }
        fn foo() {
            let a = (2 + 3 - f());
        }
    """, checkWeakWarn = true)

    fun `test remove cast inside complex expr`() = checkFixByText("Remove `as i32`", """
        fn foo() {
            let a = 1 - 2i32 /*weak_warning descr="Unnecessary cast"*/as /*caret*/i32/*weak_warning**/ + 3;
        }
    """, """
        fn foo() {
            let a = 1 - 2i32 + 3;
        }
    """, checkWeakWarn = true)


    fun `test remove cast with weird formatting`() = checkFixByText("Remove `as i32`", """
        fn foo() {
            let a = 1i32 /*weak_warning descr="Unnecessary cast"*/as    /*caret*/
        i32/*weak_warning**/;
        }
    """, """
        fn foo() {
            let a = 1i32;
        }
    """, checkWeakWarn = true)

    fun `test necessary fn casts`() = checkWarnings("""
        fn main() {
            let mut a = foo as fn();
            a = bar as fn();

            let add = |x, y| x + y;
            let b = add as fn(i32, i32) -> i32;

            let c = foo;
            let d = c as fn();
        }

        fn foo() {}
        fn bar() {}
    """)


    fun `test unnecessary cast as fn pointer`() = checkWarnings("""
        fn main() {
            let a: fn() = foo;
            let b: fn() = a /*weak_warning descr="Unnecessary cast"*/as fn()/*weak_warning**/;

            let c: fn() = || {};
            let d: fn() = c /*weak_warning descr="Unnecessary cast"*/as fn()/*weak_warning**/;

            let a: fn() = foo;
            let b = a /*weak_warning descr="Unnecessary cast"*/as fn()/*weak_warning**/;

            let c: fn() = || {};
            let d = c /*weak_warning descr="Unnecessary cast"*/as fn()/*weak_warning**/;
        }

        fn foo() {}
    """)

    fun `test enum variant cast`() = checkWarnings("""
        fn foo() {
            enum Foo {
                Y(u32),
                Z(u32)
            }

            let mut a = Foo::Y as fn(u32) -> Foo;
            a = Foo::Z as fn(u32) -> Foo;
    }
    """)

    fun `test struct cast`() = checkWarnings("""
        struct S(i32, u8);
        fn foo() {
            let a = S as fn(i32, u8) -> S;
        }
    """)

    fun `test struct impl cast`() = checkWarnings("""
        struct S(i32);

        impl S {
            fn new() {
                let a = Self as fn(i32) -> S;
            }
        }
    """)

    /**
     * TODO: fix this false-positive
     *
     * This code doesn't compile, because `b` has type `i32` and `c` has type `i64`
     * Removing `as i32` cast will make this code compile and change types of both `a` and `b` to `i64`
     *
     * However, inferred type for `a` is `i32`, so casting `b` to `i32` is highlighted as unnecessary cast
     */
    @Test(expected = ComparisonFailure::class)
    fun `test known false-positive`() = checkWarnings("""
        fn foo() {
            let a = 1;
            let b = a as i32;
            let c: i64 = b;
        }
    """)
}
