/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongGenericArgumentsOrderInspectionTest : RsInspectionsTestBase(RsWrongGenericArgumentsOrderInspection::class) {

    fun `test mismatch (expr)`() = checkByText("""
        struct SFoo<T>(T);
        struct SBar<const N: i32>;
        struct SBaz<'a>(&'a str);

        trait TFoo<T> {}
        trait TBar<const N: i32> {}
        trait TBaz<'a> {}

        fn foo<T>() {}
        fn bar<const N: i32>() {}
        fn baz<'a>() {}

        impl<T> TFoo<T> for SFoo<T> {}
        impl<const N: i32> TFoo<<error descr="Constant provided when a type was expected [E0747]">N</error>> for SFoo<<error descr="Constant provided when a type was expected [E0747]">N</error>> {}
        impl<'a> TFoo<<error descr="Lifetime provided when a type was expected [E0747]">'a</error>> for SFoo<<error descr="Lifetime provided when a type was expected [E0747]">'a</error>> {}

        impl<T> TBar<<error descr="Type provided when a constant was expected [E0747]">T</error>> for SBar<<error descr="Type provided when a constant was expected [E0747]">T</error>> {}
        impl<const N: i32> TBar<N> for SBar<N> {}
        impl<'a> TBar<<error descr="Lifetime provided when a constant was expected [E0747]">'a</error>> for SBar<<error descr="Lifetime provided when a constant was expected [E0747]">'a</error>> {}

        impl<T> TBaz<T> for SBaz<T> {}
        impl<const N: i32> TBaz<N> for SBaz<N> {}
        impl<'a> TBaz<'a> for SBaz<'a> {}

        fn test<'a, T, const N: i32>() {
            foo::<T>();
            foo::<<error descr="Constant provided when a type was expected [E0747]">N</error>>();
            foo::<<error descr="Lifetime provided when a type was expected [E0747]">'a</error>>();

            bar::<<error descr="Type provided when a constant was expected [E0747]">T</error>>();
            bar::<N>();
            bar::<<error descr="Lifetime provided when a constant was expected [E0747]">'a</error>>();

            baz::<T>();
            baz::<N>();
            baz::<'a>();
        }
    """)

    fun `test const argument ambiguity`() = checkFixByText("Enclose the expression in braces", """
        struct N { f: i32 }
        fn foo<const N: i32>() {
            foo::<<error descr="Type provided when a constant was expected [E0747]">N/*caret*/</error>>();
        }
    """, """
        struct N { f: i32 }
        fn foo<const N: i32>() {
            foo::<{ N }>();
        }
    """)

    fun `test path exprs`() = checkByText("""
        fn foo<T, const C: i32>() {}
        const C1: i32 = 1;
        const C2: bool = true;
        struct S;

        fn main() {
            foo::<i32, 1>;
            foo::<<error descr="Constant provided when a type was expected [E0747]">1</error>, <error descr="Type provided when a constant was expected [E0747]">i32</error>>;
            foo::<S, C1>;
            foo::<<error descr="Constant provided when a type was expected [E0747]">C1</error>, <error descr="Type provided when a constant was expected [E0747]">S</error>>;
        }
    """)

    fun `test func as const argrument`() = checkByText("""
        struct S<T, const C: usize>;
        fn foo() {}
        fn main() { S::<<error descr="Constant provided when a type was expected [E0747]">foo</error>, foo>; }
    """)
}
