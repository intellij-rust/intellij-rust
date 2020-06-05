/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.RsTypeArgumentList

/**
 * Tests for RsGenericParameterInfoHandler
 */
class RsGenericParameterInfoHandlerTest
    : RsParameterInfoHandlerTestBase<RsTypeArgumentList, HintLine>(RsGenericParameterInfoHandler()) {
    fun `test fn no params`() = checkByText("""
        fn foo(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "", "", -1)

    fun `test fn one param`() = checkByText("""
        fn foo<T>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T", "", 0)

    fun `test fn before lbrace`() = checkByText("""
        fn foo<T>(x: T) {}
        fn main() { foo::/*caret*/<>(); }
    """, "T", "", 0)

    fun `test fn after rbrace`() = checkByText("""
        fn foo<T>(x: T) {}
        fn main() { foo::<>/*caret*/(); }
    """, "", "", -1)

    fun `test fn between colons`() = checkByText("""
        fn foo<T>(x: T) {}
        fn main() { foo:/*caret*/:<>(); }
    """, "", "", -1)

    fun `test fn second param`() = checkByText("""
        fn foo<T, P>(x: T) {}
        fn main() { foo::<i32,/*caret*/>(); }
    """, "T, P", "", 1)

    fun `test fn param out of bounds`() = checkByText("""
        fn foo<T>(x: T) {}
        fn main() { foo::<i32, /*caret*/>(); }
    """, "T", "", 1)

    fun `test fn bounded`() = checkByText("""
        trait Trait {}
        fn foo<T: Trait>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: Trait", "", 0)

    fun `test fn multiple bounds`() = checkByText("""
        trait Trait {}
        trait OtherTrait {}
        fn foo<T: Trait + OtherTrait>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: Trait + OtherTrait", "", 0)

    fun `test fn bounds with supertraits`() = checkByText("""
        trait Trait {}
        trait OtherTrait : Trait{}
        fn foo<T: OtherTrait>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: OtherTrait", "", 0)

    fun `test fn bounds with ?Sized`() = checkByText("""
        pub trait Trait {}
        fn foo<T: Trait + ?Sized>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: ?Sized + Trait", "", 0)

    fun `test fn bounds with Sized`() = checkByText("""
        pub trait Trait {}
        fn foo<T: Trait + Sized>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: Trait", "", 0)

    // TODO: hint = "T", after intellij-rust#2783 fix
    fun `test fn bounds with ?Sized and Sized`() = checkByText("""
        #[lang = "sized"]
        pub trait Sized {}
        fn foo<T: Sized + ?Sized>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: ?Sized", "", 0)

    // TODO: hint = "T: Trait", after intellij-rust#2783 fix
    fun `test fn bounds with ?Sized and derived Sized`() = checkByText("""
        #[lang = "sized"]
        pub trait Sized {}
        pub trait Trait : Sized {}
        fn foo<T: Trait + ?Sized>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: ?Sized + Trait", "", 0)

    fun `test fn with simple where`() = checkByText("""
        pub trait Trait {}
        fn foo<T>(x: T) where T: Trait {}
        fn main() { foo::</*caret*/>(); }
    """, "T: Trait", "", 0)

    fun `test fn with complicated where`() = checkByText("""
        pub trait ConvertTo<T> {}
        fn foo<T>(x: T) where i32: ConvertTo<T> {}
        fn main() { foo::</*caret*/>(); }
    """, "T", "where i32: ConvertTo<T>", 0)

    fun `test fn with complicated where 2`() = checkByText("""
        enum E<T> { Foo(T), }
        pub trait SuperTrait {}
        fn foo<T>(x: T) where E<T>: SuperTrait {}
        fn main() { foo::</*caret*/>(); }
    """, "T", "where E<T>: SuperTrait", 0)

    fun `test fn with bounds and simple where`() = checkByText("""
        pub trait Trait {}
        pub trait OtherTrait {}
        fn foo<T: OtherTrait>(x: T) where T: Trait {}
        fn main() { foo::</*caret*/>(); }
    """, "T: OtherTrait + Trait", "", 0)

    fun `test enum`() = checkByText("""
        enum E<T> { Foo(T) }
        fn main() { E::</*caret*/>::Foo(); }
    """, "T", "", 0)

    fun `test struct`() = checkByText("""
        struct S<T> { field: T, }
        fn main() { S::</*caret*/> { field: }; }
    """, "T", "", 0)

    fun `test nested generics`() = checkByText("""
        struct S<T> { field: T, }
        fn foo<P>(x: T) {}
        fn main() { foo::<S</*caret*/>>(); }
    """, "T", "", 0)

    fun `test nested generics 2`() = checkByText("""
        struct S<T> { field: T, }
        fn foo<P, K>(x: T) {}
        fn main() { foo::<S<i32>, /*caret*/>(); }
    """, "P, K", "", 1)

    fun `test parametrized bounds`() = checkByText("""
        pub trait Trait<T> {}
        struct S<T> { x: T, }
        fn foo<T: Trait<S<i32>>>(x: T) {}
        fn main() { foo::</*caret*/>(); }
    """, "T: Trait<S<i32>>", "", 0)

    fun `test dotted method call`() = checkByText("""
        struct S;
        impl<T> S { fn foo<T>(&self, x: T) {} }
        fn main() {
        let s = S{};
        s.foo::</*caret*/>(5); }
    """, "T", "", 0)

    fun `test explicit self method call`() = checkByText("""
        struct S;
        impl<T> S { fn foo<T>(&self, x: T) {} }
        fn main() {
        let s = S{};
        S::foo::</*caret*/>(&s, 5); }
    """, "T", "", 0)

    fun `test trait method`() = checkByText("""
        trait Trait {
            fn in_trait<T>(&self, x: T) {}
            fn another<T>(y: T) {}
        }
        struct S;
        impl Trait for S {}
        fn main() {
            let s = S {};
            s.in_trait::</*caret*/>();
        }
    """, "T", "", 0)

    fun `test trait method 2`() = checkByText("""
        trait Trait {
            fn in_trait<T>(&self, x: T) {}
            fn another<T>(y: T) {}
        }
        struct S;
        impl Trait for S {}
        fn main() {
            let s = S {};
            S::another::</*caret*/>();
        }
    """, "T", "", 0)

    fun `test in fn declaration`() = checkByText("""
        fn foo</*caret*/>(x: T) {}
    """, "", "", -1)

    fun `test in struct declaration`() = checkByText("""
        struct S</*caret*/> { field: T, }
    """, "", "", -1)

    fun `test in enum declaration`() = checkByText("""
        enum E</*caret*/> { Foo(T), }
    """, "", "", -1)

    fun `test in trait declaration`() = checkByText("""
        pub trait Trait</*caret*/> {
            fn foo(x: T);
        }
    """, "", "", -1)

    fun `test after impl`() = checkByText("""
        pub trait Trait<T> {}
        struct S;
        impl</*caret*/> Trait<T> for S { }
    """, "", "", -1)

    fun `test in impl block`() = checkByText("""
        pub trait Trait<T> {}
        struct S<K> {x: K}
        impl<P> Trait</*caret*/> for S<P> { }
    """, "T", "", 0)

    private fun checkByText(@Language("Rust") code: String, hint: String, where: String, index: Int) {
        val hints = if (where.isEmpty()) {
            arrayOf(hint to index)
        } else {
            arrayOf(
                hint to index,
                where to 0
            )
        }
        checkByText(code, *hints)
    }
}
