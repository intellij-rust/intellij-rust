/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.borrowck

import org.rust.ExpandMacros
import org.rust.MockAdditionalCfgOptions
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.ide.inspections.RsBorrowCheckerInspection
import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.lang.core.macros.MacroExpansionScope

class RsBorrowCheckerDerivativeTest : RsInspectionsTestBase(RsBorrowCheckerInspection::class) {
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error on derivative copy`() = checkByText("""
        use derivative::Derivative;

        #[derive(Derivative)]
        #[derivative(Clone, Copy)]
        struct S(i32);

        fn mov(s: &S) -> S {
            *s
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move error on derivative copy with non-movable generic`() = checkByText("""
        use derivative::Derivative;

        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone, Copy)]
        struct S<T>(T);

        fn mov(s: &S<Foo>) -> S {
            <error descr="Cannot move">*s</error>
        }
    """, checkWarn = false)


    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error on derivative copy when bound is explicitly specified as empty`() = checkByText("""
        use derivative::Derivative;

        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = ""), Copy(bound = ""))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            *s
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move error on derivative copy when bound is explicitly specified as typical and unfulfilled`() = checkByText("""
        use derivative::Derivative;

        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = "T: Clone"), Copy(bound = "T: Copy"))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            <error descr="Cannot move">*s</error>
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move error on derivative copy when bound is explicitly specified as typical and unfulfilled (alt)`() = checkByText("""
        use derivative::Derivative;

        #[derive(Debug)]
        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = "T: Clone"), Copy(bound = "T: Copy"))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            <error descr="Cannot move">*s</error>
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error on derivative copy when bound is explicitly specified as typical and fulfilled`() = checkByText("""
        use derivative::Derivative;

        #[derive(Clone, Copy)]
        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = "T: Clone"), Copy(bound = "T: Copy"))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            *s
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test move error on derivative copy when bound is explicitly specified as atypical and unfulfilled`() = checkByText("""
        use derivative::Derivative;

        #[derive(Clone, Copy)]
        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = "T: Debug"), Copy(bound = "T: Debug"))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            <error descr="Cannot move">*s</error>
        }
    """, checkWarn = false)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no move error on derivative copy when bound is explicitly specified as atypical and fulfilled`() = checkByText("""
        use derivative::Derivative;

        #[derive(Debug)]
        struct Foo;

        #[derive(Derivative)]
        #[derivative(Clone(bound = "T: Debug"), Copy(bound = "T: Debug"))]
        struct S<T>(PhantomData<T>);

        fn mov(s: &S<Foo>) -> S {
            *s
        }
    """, checkWarn = false)
}
