/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

class RsWrongGenericArgumentsOrderInspectionTest : RsInspectionsTestBase(RsWrongGenericArgumentsOrderInspection::class) {

    fun `test mismatch (expr)`() = checkByText("""
        struct Foo<T>(T);
        struct Bar<const N: i32>;
        fn foo<T>() {}
        fn bar<const N: i32>() {}
        fn baz<'a, T, const N: i32>(
            p1: Foo<T>,
            p2: Foo<<error descr="Constant provided when a type was expected [E0747]">N</error>>,
            p3: Bar<<error descr="Type provided when a constant was expected [E0747]">T</error>>,
            p4: Bar<N>
        ) {
            foo::<T>();
            foo::<<error descr="Constant provided when a type was expected [E0747]">N</error>>();
            bar::<<error descr="Type provided when a constant was expected [E0747]">T</error>>();
            bar::<N>();
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
