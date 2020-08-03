/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsWrongTypeArgumentsNumberInspectionTest : RsInspectionsTestBase(RsWrongTypeArgumentsNumberInspection::class) {
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test ignores Fn-traits`() = checkByText("""
        fn foo(f: &mut FnOnce(u32) -> bool) {}  // No annotation despite the fact that FnOnce has a type parameter
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test ignores Fn-traits in type bounds`() = checkByText("""
        fn foo<F: Fn(&str) -> u32>() {}
    """)

    fun `test ignores Self type`() = checkByText("""
        struct Foo<T> { t: T }
        impl<T> Foo<T> {
            fn foo(s: Self) {}
        }
    """)

    fun `test too few type arguments struct`() = checkByText("""
        struct Foo1<T> { t: T }
        struct Foo2<T, U> { t: T, u: U }
        struct Foo2to3<T, U, V = bool> { t: T, u: U, v: V }

        struct Ok {
            ok1: Foo1<u32>,
            ok2: Foo2<u32, bool>,
            ok3: Foo2to3<u8, u8>,
            ok4: Foo2to3<u8, u8, u8>
        }

        struct Err {
            err1: <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">Foo1</error>,
            err2: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">Foo2<u32></error>,
            err3: <error descr="Wrong number of type arguments: expected at least 2, found 1 [E0107]">Foo2to3<u32></error>,
        }

        impl <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">Foo1</error> {}
        fn err(f: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">Foo2<u32></error>) -> <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">Foo1</error> {}
        type Type = <error descr="Wrong number of type arguments: expected at least 2, found 1 [E0107]">Foo2to3<u8></error>;
    """)

    fun `test too many type arguments struct`() = checkByText("""
        struct Foo0;
        struct Foo1<T> { t: T }
        struct Foo1to2<T, U = bool> { t: T, u: U }

        struct Ok {
            ok1: Foo0,
            ok2: Foo1<u32>,
            ok3: Foo1to2<u8>,
            ok4: Foo1to2<u8, bool>,
        }

        struct Err {
            err1: <error descr="Wrong number of type arguments: expected 0, found 2 [E0107]">Foo0<u32, bool></error>,
            err2: <error descr="Wrong number of type arguments: expected 1, found 2 [E0107]">Foo1<u8, f64></error>,
            err3: <error descr="Wrong number of type arguments: expected at most 2, found 3 [E0107]">Foo1to2<u32, f32, bool></error>,
        }

        impl <error descr="Wrong number of type arguments: expected 0, found 1 [E0107]">Foo0<u8></error> {}
        fn err(f: <error descr="Wrong number of type arguments: expected 1, found 2 [E0107]">Foo1<u32, bool></error>) -> <error descr="Wrong number of type arguments: expected at most 2, found 3 [E0107]">Foo1to2<u8, u8, u8></error> {}
        type Type = <error descr="Wrong number of type arguments: expected 1, found 3 [E0107]">Foo1<u8, bool, f64></error>;
    """)

    fun `test missing arguments in struct`() = checkByText("""
        struct S<T> { t: T }

        fn main() {
            let x: S</*caret*/u32>;
        }
    """)

    fun `test wrong number of type arguments method`() = checkByText("""
        struct Test;

        impl Test {
            fn method1<T, R>(&self, u: &[T], v: &[R]){}
        }

        fn main() {
            let x = Test;
            let u = &[0];
            let v = &[0];

            x.<error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">method1::<i32>(u, v)</error>;
            x.<error descr="Wrong number of type arguments: expected 2, found 3 [E0107]">method1::<i32, i32, i32>(u, v)</error>;
            x.method1::<>(u, v);
            x.method1(u, v);
        }
    """)

    fun `test wrong number of type arguments function call`() = checkByText("""
        fn foo<T, R>(u: &[T], v: &[R]){}

        fn main() {
            let u = &[0];
            let v = &[0];

            <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">foo::<i32>(u, v)</error>;
            <error descr="Wrong number of type arguments: expected 2, found 3 [E0107]">foo::<i32, i32, i32>(u, v)</error>;
            foo::<>(u, v);
            foo(u, v);
        }
    """)

    fun `test fix no type arguments struct`() = checkFixByText("Remove redundant type arguments", """
        struct Foo0;
        impl <error>Foo0/*caret*/<u8></error> {}
    """, """
        struct Foo0;
        impl Foo0 {}
    """)

    fun `test fix no type arguments method`() = checkFixByText("Remove redundant type arguments", """
        struct Test;

        impl Test {
            fn method(&self) {}
        }

        fn main() {
            let x = Test;
            x.<error descr="Wrong number of type arguments: expected 0, found 1 [E0107]">/*caret*/method::<i32>()</error>;
        }
    """, """
        struct Test;

        impl Test {
            fn method(&self) {}
        }

        fn main() {
            let x = Test;
            x.method();
        }
    """)

    fun `test fix no type function call`() = checkFixByText("Remove redundant type arguments", """
        fn foo() {}

        fn main() {
            <error descr="Wrong number of type arguments: expected 0, found 1 [E0107]">foo/*caret*/::<i32>()</error>;
        }
    """, """
        fn foo() {}

        fn main() {
            foo();
        }
    """)

    fun `test type arguments missing in call`() = checkByText("""
        fn foo<V, T>(){}

        fn main() {
            foo();
        }
    """)

    fun `test don't explode for non-paths`() = checkByText("""
        fn foo() {}
        fn main() {
            (foo)();
        }
    """)

    fun `test fix struct with multiple type arguments`() = checkFixByText("Remove redundant type arguments", """
        struct Foo<T, U> { t: T, u: U }
        struct Err {
            err1: <error descr="Wrong number of type arguments: expected 2, found 4 [E0107]">Foo<u32, i32, u32, u32/*caret*/></error>,
        }
    """, """
        struct Foo<T, U> { t: T, u: U }
        struct Err {
            err1: Foo<u32, i32>,
        }
    """)

    fun `test fix struct with default type arguments`() = checkFixByText("Remove redundant type arguments", """
        struct Foo<T, U = i32> { t: T, u: U }
        struct Err {
            err1: <error descr="Wrong number of type arguments: expected at most 2, found 4 [E0107]">Foo<u32, i32, u32, u32/*caret*/></error>,
        }
    """, """
        struct Foo<T, U = i32> { t: T, u: U }
        struct Err {
            err1: Foo<u32, i32>,
        }
    """)

    fun `test dyn trait`() = checkByText("""
        trait Trait<A> {}
        fn foo() {
            let x: &dyn <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">Trait<></error>;
        }
    """)

    fun `test impl trait`() = checkByText("""
        trait Trait<A> {}
        fn foo(_: impl <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">Trait<></error>) {}
    """)

    fun `test add arguments missing arguments`() = checkFixByText("Add missing type arguments", """
        struct S<T> { t: T }

        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">S/*caret*/</error>;
        }
    """, """
        struct S<T> { t: T }

        fn main() {
            let x: S<T>;
        }
    """)

    fun `test add arguments empty arguments`() = checkFixByText("Add missing type arguments", """
        struct S<T> { t: T }

        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">S</*caret*/></error>;
        }
    """, """
        struct S<T> { t: T }

        fn main() {
            let x: S<T>;
        }
    """)

    fun `test add arguments copy existing arguments`() = checkFixByText("Add missing type arguments", """
        struct S<T, R> { t: T, r: R }

        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">S<u32/*caret*/></error>;
        }
    """, """
        struct S<T, R> { t: T, r: R }

        fn main() {
            let x: S<u32, R>;
        }
    """)

    fun `test add arguments copy existing lifetime`() = checkFixByText("Add missing type arguments", """
        struct S<'a, T> { t: &'a T }

        fn foo<'a>(x: &'a u32) {
            let x: <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">S<'a/*caret*/></error>;
        }
    """, """
        struct S<'a, T> { t: &'a T }

        fn foo<'a>(x: &'a u32) {
            let x: S<'a, T>;
        }
    """)

    fun `test add arguments keep lifetimes`() = checkFixByText("Add missing type arguments", """
        struct S<'a, T, R> { t: &'a T, r: R }

        fn foo<'a>(x: &'a u32) {
            let x: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">S<'a, u32/*caret*/></error>;
        }
    """, """
        struct S<'a, T, R> { t: &'a T, r: R }

        fn foo<'a>(x: &'a u32) {
            let x: S<'a, u32, R>;
        }
    """)

    fun `test add arguments keep associated types`() = checkFixByText("Add missing type arguments", """
        trait S<A, B> {
            type Item;
        }

        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">S<u32, Item=u32/*caret*/></error>;
        }
    """, """
        trait S<A, B> {
            type Item;
        }

        fn main() {
            let x: S<u32, B, Item=u32>;
        }
    """)

    fun `test add arguments ignore type parameters with a default`() = checkFixIsUnavailable("Add missing type arguments", """
        struct S<A, B=u32, C=u32>(A, B, C);

        fn main() {
            let x: S<u32, u32>;
        }
    """)

    fun `test add arguments keep comments and whitespace`() = checkFixByText("Add missing type arguments", """
        trait Trait<'a, A, B> {
            type Item;
            fn foo(&self) -> (&'a u32, A, B);
        }
        fn foo<'a>(_: &'a u32) {
            let x: &<error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">Trait< /*'a*/ 'a    /*'a*/, /*A*/   u32 /*A*/,   /*Item*/ Item = u32 /*Item*/  >/*caret*/</error>;
        }
    """, """
        trait Trait<'a, A, B> {
            type Item;
            fn foo(&self) -> (&'a u32, A, B);
        }
        fn foo<'a>(_: &'a u32) {
            let x: &Trait< /*'a*/ 'a    /*'a*/, /*A*/   u32 /*A*/, B, /*Item*/ Item = u32 /*Item*/  >;
        }
    """)

    fun `test add arguments keep trailing comma`() = checkFixByText("Add missing type arguments", """
        struct S<T, R>(T, R);
        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">S<u32,>/*caret*/</error>;
        }
    """, """
        struct S<T, R>(T, R);
        fn main() {
            let x: S<u32, R, >;
        }
    """)

    fun `test add arguments to function call`() = checkFixByText("Add missing type arguments", """
        fn foo<S, T>() -> (S, T) { unreachable!() }

        fn main() {
            <error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">foo::<u32/*caret*/>()</error>;
        }
    """, """
        fn foo<S, T>() -> (S, T) { unreachable!() }

        fn main() {
            foo::<u32, T>();
        }
    """)

    fun `test add arguments to method call`() = checkFixByText("Add missing type arguments", """
        struct S;
        impl S {
            fn foo<S, T>(&self) -> (S, T) {
                unreachable!()
            }
        }

        fn foo(s: S) {
            s.<error descr="Wrong number of type arguments: expected 2, found 1 [E0107]">foo::<u32>()/*caret*/</error>;
        }
    """, """
        struct S;
        impl S {
            fn foo<S, T>(&self) -> (S, T) {
                unreachable!()
            }
        }

        fn foo(s: S) {
            s.foo::<u32, T>();
        }
    """)

    fun `test add arguments keep path format`() = checkFixByText("Add missing type arguments", """
        mod foo {
            pub struct S<T>(T);
        }

        fn main() {
            let x: <error descr="Wrong number of type arguments: expected 1, found 0 [E0107]">foo::   S/*caret*/</error>;
        }
    """, """
        mod foo {
            pub struct S<T>(T);
        }

        fn main() {
            let x: foo::   S<T>;
        }
    """)
}
