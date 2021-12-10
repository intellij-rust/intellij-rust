/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.rust.ide.annotator.RsAnnotatorTestBase
import org.rust.ide.annotator.RsErrorAnnotator

class AddMissingSupertraitImplFixTest : RsAnnotatorTestBase(RsErrorAnnotator::class) {
    fun `test empty supertrait`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B: A {}

        struct S;

        <error>impl <error>B/*caret*/</error> for S</error> {}
    """, """
        trait A {}
        trait B: A {}

        struct S;

        impl A for S {}

        impl B/*caret*/ for S {}
    """)

    fun `test supertrait with items`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {
            type FOO;
            const BAR: u32;
            fn foo(&self);
        }
        trait B: A {}

        struct S;

        <error>impl <error>B/*caret*/</error> for S</error> {}
    """, """
        trait A {
            type FOO;
            const BAR: u32;
            fn foo(&self);
        }
        trait B: A {}

        struct S;

        impl A for S {
            type FOO = ();
            const BAR: u32 = 0;

            fn foo(&self) {
                todo!()
            }
        }

        impl B/*caret*/ for S {}
    """)

    fun `test multiple supertraits`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B {}
        trait C: A + B {}

        struct S;

        <error>impl <error>C/*caret*/</error> for S</error> {}
    """, """
        trait A {}
        trait B {}
        trait C: A + B {}

        struct S;

        impl A for S {}

        impl B for S {}

        impl C/*caret*/ for S {}
    """)

    fun `test grandparent supertrait`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B: A {}
        trait C: B {}

        struct S;

        <error>impl <error>C/*caret*/</error> for S</error> {}
    """, """
        trait A {}
        trait B: A {}
        trait C: B {}

        struct S;

        impl B for S {}

        impl A for S {}

        impl C/*caret*/ for S {}
    """)

    fun `test do not implement supertrait multiple times`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B: A {}
        trait C: B + A {}

        struct S;

        <error>impl <error>C/*caret*/</error> for S</error> {}
    """, """
        trait A {}
        trait B: A {}
        trait C: B + A {}

        struct S;

        impl B for S {}

        impl A for S {}

        impl C/*caret*/ for S {}
    """)

    fun `test implement supertrait multiple times with different generic arguments`() = checkFixByText("Implement missing supertrait(s)", """
        trait A<T> {}
        trait B: A<u32> {}
        trait C: B + A<bool> {}

        struct S;

        <error>impl <error>C/*caret*/</error> for S</error> {}
    """, """
        trait A<T> {}
        trait B: A<u32> {}
        trait C: B + A<bool> {}

        struct S;

        impl B for S {}

        impl A<u32> for S {}

        impl A<bool> for S {}

        impl C/*caret*/ for S {}
    """)

    fun `test already implemented trait`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B {}
        trait C: A + B {}

        struct S;

        impl B for S {}

        <error>impl <error>C/*caret*/</error> for S</error> {}
    """, """
        trait A {}
        trait B {}
        trait C: A + B {}

        struct S;

        impl B for S {}

        impl A for S {}

        impl C/*caret*/ for S {}
    """)

    fun `test implemented trait for specific generic argument`() = checkFixByText("Implement missing supertrait(s)", """
        trait A<T> {
            fn foo(&self) -> T;
        }
        trait C<T>: A<T> {}

        struct S;

        <error>impl <error>C<u32>/*caret*/</error> for S</error> {}
    """, """
        trait A<T> {
            fn foo(&self) -> T;
        }
        trait C<T>: A<T> {}

        struct S;

        impl A<u32> for S {
            fn foo(&self) -> u32 {
                todo!()
            }
        }

        impl C<u32>/*caret*/ for S {}
    """)

    fun `test trait partially implemented for specific type`() = checkFixByText("Implement missing supertrait(s)", """
        trait A<T> {}
        trait C<T>: A<T> {}

        struct S;

        impl A<u32> for S {}

        <error>impl <R> <error>C<R>/*caret*/</error> for S</error> {}
    """, """
        trait A<T> {}
        trait C<T>: A<T> {}

        struct S;

        impl A<u32> for S {}

        impl<R> A<R> for S {}

        impl <R> C<R>/*caret*/ for S {}
    """)

    fun `test generic type generic type argument`() = checkFixByText("Implement missing supertrait(s)", """
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S<T>(T);

        <error>impl <R> <error>B<u32>/*caret*/</error> for S<R></error> {}
    """, """
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S<T>(T);

        impl<R> A<u32> for S<R> {}

        impl <R> B<u32> for S<R> {}
    """)

    fun `test generic type specific type argument`() = checkFixByText("Implement missing supertrait(s)", """
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S<T>(T);

        <error>impl <error>B<u32>/*caret*/</error> for S<bool></error> {}
    """, """
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S<T>(T);

        impl A<u32> for S<bool> {}

        impl B<u32> for S<bool> {}
    """)

    // TODO: psiSubst merging?
    fun `test recursive generics`() = checkFixByText("Implement missing supertrait(s)", """
        trait T1<A> {}
        trait T2<B>: T1<B> {}
        trait T3<D>: T2<D> {}
        trait T4<R>: T3<R> {}
        trait T5<C>: T4<C> {}
        trait T6<D>: T5<D> {}
        trait T7<E>: T6<E> {}

        struct S<T>(T);

        <error>impl <R> <error>T7<R>/*caret*/</error> for S<R></error> {}
    """, """
        trait T1<A> {}
        trait T2<B>: T1<B> {}
        trait T3<D>: T2<D> {}
        trait T4<R>: T3<R> {}
        trait T5<C>: T4<C> {}
        trait T6<D>: T5<D> {}
        trait T7<E>: T6<E> {}

        struct S<T>(T);

        impl<R> T6<R> for S<R> {}

        impl<R> T5<R> for S<R> {}

        impl<R> T4<R> for S<R> {}

        impl<R> T3<R> for S<R> {}

        impl<R> T2<R> for S<R> {}

        impl<R> T1<R> for S<R> {}

        impl <R> T7<R> for S<R> {}
    """)

    fun `test filter unused type parameters`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B<T>: A {}

        struct S<T>(T);

        <error>impl <R> <error>B<R>/*caret*/</error> for S<u32></error> {}
    """, """
        trait A {}
        trait B<T>: A {}

        struct S<T>(T);

        impl A for S<u32> {}

        impl <R> B<R> for S<u32> {}
    """)

    fun `test filter unused where clause`() = checkFixByText("Implement missing supertrait(s)", """
        trait A {}
        trait B<T>: A {}

        struct S<T>(T);

        <error>impl <R> <error>B<R>/*caret*/</error> for S<u32></error> where R: A {}
    """, """
        trait A {}
        trait B<T>: A {}

        struct S<T>(T);

        impl A for S<u32> {}

        impl <R> B<R> for S<u32> where R: A {}
    """)

    fun `test import trait`() = checkFixByText("Implement missing supertrait(s)", """
        mod foo {
            pub trait A {}
            pub trait B: A {}
        }

        struct S;

        <error>impl <error>foo::B/*caret*/</error> for S</error> {}
    """, """
        use crate::foo::A;

        mod foo {
            pub trait A {}
            pub trait B: A {}
        }

        struct S;

        impl A for S {}

        impl foo::B/*caret*/ for S {}
    """)
}

// TODO: all kinds of bounds and generics
