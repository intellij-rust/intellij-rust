/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongTypeParametersNumberInspectionTest : RsInspectionsTestBase(RsWrongTypeParametersNumberInspection()) {

    fun `test E0243 number of type parameters is less than expected`() = checkByText("""
        struct Foo1<T> { t: T }
        struct Foo2<T, U> { t: T, u: U }
        struct Foo2to3<T, U, V = bool> { t: T, u: U, v: V }

        struct Ok {
            ok1: Foo1<u32>,
            ok2: Foo2<u32, bool>,
            ok3: Foo2to3<u8, u8>,
        }

        struct Err {
            err1: <error descr="Wrong number of type parameters: expected 1, found 0 [E0243]">Foo1</error>,
            err2: <error descr="Wrong number of type parameters: expected 2, found 1 [E0243]">Foo2<u32></error>,
            err3: <error descr="Wrong number of type parameters: expected at least 2, found 1 [E0243]">Foo2to3<u32></error>,
        }

        impl <error>Foo1</error> {}
        fn err(f: <error>Foo2<u32></error>) -> <error>Foo1</error> {}
        type Type = <error>Foo2to3<u8></error>;
    """)

    fun `test E0243 ignores Fn-traits`() = checkByText("""
        fn foo(f: &mut FnOnce(u32) -> bool) {}  // No annotation despite the fact that FnOnce has a type parameter
    """)

    fun `test E0243 ignores Self type`() = checkByText("""
        struct Foo<T> { t: T }
        impl<T> Foo<T> {
            fn foo(s: Self) {}
        }
    """)

    fun `test E0244 number of type parameters is greater than expected`() = checkByText("""
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
            err1: <error descr="Wrong number of type parameters: expected 0, found 2 [E0244]">Foo0<u32, bool></error>,
            err2: <error descr="Wrong number of type parameters: expected 1, found 2 [E0244]">Foo1<u8, f64></error>,
            err3: <error descr="Wrong number of type parameters: expected at most 2, found 3 [E0244]">Foo1to2<u32, f32, bool></error>,
        }

        impl <error>Foo0<u8></error> {}
        fn err(f: <error>Foo1<u32, bool></error>) -> <error>Foo1to2<u8, u8, u8></error> {}
        type Type = <error>Foo1<u8, bool, f64></error>;
    """)

    fun `test fix E0244 no type parameters method `() = checkFixByText("Remove all type parameter", """
        struct Foo0;
        impl <error>Foo0<caret><u8></error> {}
    """, """
        struct Foo0;
        impl Foo0 {}
    """)

    fun `test E0035 no type parameters method `() = checkByText("""
        struct Test;

        impl Test {
            fn method(&self) {}
        }

        fn main() {
            let x = Test;

            x.<error descr="Wrong number of type parameters: expected 0, found 1 [E0035]">method::<i32>()</error>;
        }
    """)

    fun `test fix E0035 no type parameters method `() = checkFixByText("Remove all type parameter", """
        struct Test;

        impl Test {
            fn method(&self) {}
        }

        fn main() {
            let x = Test;

            x.<error>method<caret>::<i32>()</error>;
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

    fun `test E0036 type parameters method`() = checkByText("""
        struct Test;

        impl Test {
            fn method<T>(&self, v: &[T]){}
            fn method_more<V, T = bool>(&self, v: &[T]){}
        }

        fn main() {
            let x = Test;
            let v = &[0];

            x.<error descr="Wrong number of type parameters: expected 1, found 2 [E0036]">method::<i32, i32>(v)</error>;
            x.<error descr="Wrong number of type parameters: expected at most 2, found 3 [E0036]">method_more::<i32, i32, i32>(v)</error>;
            x.method(v);
            x.method_more(v);
        }
    """)

    fun `test E0087 number of type parameters is greater than expected call`() = checkByText("""
        fn foo<T>() {}
        fn foo_more<T = bool>(){}

        fn main() {
            <error descr="Wrong number of type parameters: expected 1, found 2 [E0087]">foo::<f64, bool>()</error>;
            <error descr="Wrong number of type parameters: expected at most 1, found 2 [E0087]">foo_more::<f64, bool>()</error>;
        }
    """)

    fun `test fix E0087 no type parameters method `() = checkFixByText("Remove all type parameter", """
        fn foo() {}

        fn main() {
            <error>foo<caret>::<i32>()</error>;
        }
    """, """
        fn foo() {}

        fn main() {
            foo();
        }
    """)

    fun `test E0089 number of type parameters is less than expected call`() = checkByText("""
        fn foo<T>() {}
        fn foo_more<V, T = bool>(){}

        fn main() {
            foo();
            foo_more();
        }
    """)

    fun `test don't explode for non-paths`() = checkByText("""
        fn foo() {}
        fn main() {
            (foo)();
        }
    """)


}
