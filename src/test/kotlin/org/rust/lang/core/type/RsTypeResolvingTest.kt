/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.intellij.lang.annotations.Language
import org.rust.ide.presentation.*
import org.rust.lang.core.macros.setContext
import org.rust.lang.core.psi.RsPathType
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.RsTypeReference
import org.rust.lang.core.psi.ext.RsGenericDeclaration
import org.rust.lang.core.resolve.ref.pathPsiSubst
import org.rust.lang.core.type.RsTypeResolvingTest.RenderMode.*
import org.rust.lang.core.types.infer.containsTyOfClass
import org.rust.lang.core.types.normType
import org.rust.lang.core.types.rawType
import org.rust.lang.core.types.ty.TyUnknown

class RsTypeResolvingTest : RsTypificationTestBase() {
    fun `test path`() = testType("""
        struct Spam;

        fn main() {
            let _: Spam = Spam;
                 //^ Spam
        }
    """)

    fun `test unit`() = testType("""
        fn main() {
            let _: () = ();
                 //^ ()
        }
    """)

    fun `test tuple`() = testType("""
        struct S;
        struct T;
        fn main() {
            let _: (S, T) = (S, T);
                 //^ (S, T)
        }
    """)

    fun `test tuple of size 1`() = testType("""
        struct S;
        fn main() {
            let _: (S,) = (S,);
                 //^ (S,)
        }
    """)

    fun `test type in parens`() = testType("""
        struct S;
        fn main() {
            let _: ((S));
                 //^ S
        }
    """)

    fun `test type of unclosed paren`() = testType("""
        struct S;
        fn main() {
            let _: (;
                 //^ <unknown>
        }
    """)

    fun `test normalizable associated type path`() = testType("""
        trait T {
            type Assoc;
        }

        struct S;

        impl T for S {
            type Assoc = S;
        }

        fn main() {
            let _: <S as T>::Assoc = S;
                 //^ <S as T>::Assoc
        }
    """)

    fun `test normalizable associated type path normalized`() = testType("""
        trait T {
            type Assoc;
        }

        struct S;

        impl T for S {
            type Assoc = S;
        }

        fn main() {
            let _: <S as T>::Assoc = S;
                 //^ S
        }
    """, normalize = true)

    fun `test enum`() = testType("""
        enum E { X }

        fn main() {
            let _: E = E::X;
                 //^ E
        }
    """)

    fun `test type item`() = testType("""
        enum E { X }

        type A = E;

        fn main() {
            let _: E = A::X;
                 //^ E
        }
    """)

    fun `test Self type`() = testType("""
        struct S;
        trait T { fn new() -> Self; }

        impl T for S { fn new() -> Self { S } }
                                  //^ S
    """)

    fun `test Self type inside impl for primitive type`() = testType("""
        trait Clone { fn clone(&self) -> Self; }
        impl Clone for i32 {
            fn clone(&self) -> Self { *self }
        }                    //^ i32
    """)

    fun `test primitive bool`() = testType("""
        type T = bool;
                  //^ bool
    """)

    fun `test primitive char`() = testType("""
        type T = char;
                  //^ char
    """)

    fun `test primitive f32`() = testType("""
        type T = f32;
                 //^ f32
    """)

    fun `test primitive f64`() = testType("""
        type T = f64;
                 //^ f64
    """)

    fun `test primitive i8`() = testType("""
        type T = i8;
                //^ i8
    """)

    fun `test primitive i16`() = testType("""
        type T = i16;
                 //^ i16
    """)

    fun `test primitive i32`() = testType("""
        type T = i32;
                 //^ i32
    """)

    fun `test primitive i64`() = testType("""
        type T = i64;
                 //^ i64
    """)

    fun `test primitive isize`() = testType("""
        type T = isize;
                   //^ isize
    """)

    fun `test primitive u8`() = testType("""
        type T = u8;
                //^ u8
    """)

    fun `test primitive u16`() = testType("""
        type T = u16;
                 //^ u16
    """)

    fun `test primitive u32`() = testType("""
        type T = u32;
                 //^ u32
    """)

    fun `test primitive u64`() = testType("""
        type T = u64;
                 //^ u64
    """)

    fun `test primitive usize`() = testType("""
        type T = usize;
                   //^ usize
    """)

    fun `test primitive str`() = testType("""
        type T = str;
                 //^ str
    """)

    fun `test primitive str ref`() = testType("""
        type T = &'static str;
                 //^ &str
    """)

    fun `test fn pointer`() = testType("""
        type T = fn(i32) -> i32;
               //^ fn(i32) -> i32
    """)

    fun `test array`() = testType("""
        type T = [i32; 2];
               //^ [i32; 2]
    """)

    fun `test array with expr`() = testType("""
        type T = [i32; 2 + 2];
               //^ [i32; 4]
    """)

    fun `test array with const`() = testType("""
        const COUNT: usize = 2;
        type T = [i32; COUNT];
               //^ [i32; 2]
    """)

    fun `test array with complex size`() = testType("""
        const COUNT: usize = 2;
        type T = [i32; (2 * COUNT + 3) << (4 / 2)];
               //^ [i32; 28]
    """)

    fun `test array with negative size`() = testType("""
        type T = [i32; 2 - 3];
               //^ [i32; <unknown>]
    """)

    fun `test array with not usize size expr`() = testType("""
        const COUNT: i32 = 2;
        type T = [i32; COUNT];
               //^ [i32; <unknown>]
    """)

    fun `test array with recursive expr`() = testType("""
        const COUNT: usize = 2 + COUNT;
        type T = [i32; COUNT];
               //^ [i32; <unknown>]
    """)

    fun `test associated type`() = testType("""
        trait Trait<T> {
            type Item;
        }
        fn foo<B: Trait<u8>>(_: B) {
            let a: B::Item;
        }           //^ <B as Trait<u8>>::Item
    """)

    fun `test associated type is not normalized when not possible`() = testType("""
        trait Trait<T> {
            type Item;
        }
        fn foo<B: Trait<u8>>(_: B) {
            let a: B::Item;
        }           //^ <B as Trait<u8>>::Item
    """, normalize = true)

    fun `test associated types for impl`() = testType("""
        trait A {
            type Item;
            fn foo(self) -> Self::Item;
        }
        struct S;
        impl A for S {
            type Item = S;
            fn foo(self) -> Self::Item { S }
        }                         //^ <S as A>::Item
    """)

    fun `test inherited associated types for impl`() = testType("""
        trait A { type Item; }
        trait B: A {
            fn foo(self) -> Self::Item;
        }
        struct S;
        impl A for S { type Item = S; }
        impl B for S {
            fn foo(self) -> Self::Item { S }
        }                         //^ <S as A>::Item
    """)

    fun `test generic trait object`() = testType("""
        trait Trait<A> {}
        fn foo(_: &Trait<u8>) { unimplemented!() }
                  //^ dyn Trait<u8>
    """)

    fun `test generic 'dyn Trait' trait object`() = testType("""
        trait Trait<A> {}
        fn foo(_: &dyn Trait<u8>) { unimplemented!() }
                  //^ dyn Trait<u8>
    """)

    fun `test trait object with bound associated type`() = testType("""
        trait Trait { type Item; }
        fn foo(_: &Trait<Item=u8>) { unimplemented!() }
                  //^ dyn Trait<Item=u8>
    """)

    fun `test impl Trait`() = testType("""
        trait Trait {}
        fn foo() -> impl Trait { unimplemented!() }
                  //^ impl Trait
    """)

    fun `test generic impl Trait`() = testType("""
        trait Trait<T> {}
        fn foo() -> impl Trait<u8> { unimplemented!() }
                  //^ impl Trait<u8>
    """)

    fun `test 'impl Trait' with bound associated type`() = testType("""
        trait Trait { type Item; }
        fn foo() -> impl Trait<Item=u8> { unimplemented!() }
                  //^ impl Trait<Item=u8>
    """)

    fun `test impl Trait1+Trait2`() = testType("""
        trait Trait1 {}
        trait Trait2 {}

        fn foo() -> impl Trait1+Trait2 { unimplemented!() }
                  //^ impl Trait1+Trait2
    """)

    fun `test primitive str ref with lifetime`() = testType("""
        type T = &'static str;
                //^ &'static str
    """, WITH_LIFETIMES)

    fun `test str ref with lifetime`() = testType("""
        type T<'a> = &'a str;
                    //^ &'a str
    """, WITH_LIFETIMES)

    fun `test str mut ref with lifetime`() = testType("""
        type T<'a> = &'a mut str;
                    //^ &'a mut str
    """, WITH_LIFETIMES)

    fun `test struct with lifetime`() = testType("""
        struct Struct<'a> {
            field: &'a i32,
        }         //^ &'a i32
    """, WITH_LIFETIMES)

    fun `test function with lifetime`() = testType("""
        fn id<'a>(x: &'a str) -> &'a str { x }
                    //^ &'a str
    """, WITH_LIFETIMES)

    fun `test impl trait with lifetime`() = testType("""
        trait Trait<'a> {
            fn foo(x: &'a str);
        }
        struct Struct {}
        impl<'b> Trait<'b> for Struct {
            fn foo(a: &'b str) {
                     //^ &'b str
            }
        }
    """, WITH_LIFETIMES)

    fun `test deep generic struct with lifetime`() = testType("""
        struct Struct<'a, T>(&'a Struct<'a, Struct<'a, &'a str>>);
                            //^ &'a Struct<'a, Struct<'a, &'a str>>
    """, WITH_LIFETIMES)

    fun `test deep generic struct with static lifetime`() = testType("""
        struct Struct<'a, T>(&'static Struct<'static, Struct<'static, &'a str>>);
                            //^ &'static Struct<'static, Struct<'static, &'a str>>
    """, WITH_LIFETIMES)

    fun `test deep generic struct with undeclared lifetime`() = testType("""
        struct Struct<'a, T>(&'b Struct<'b, Struct<'b, &'a str>>);
                            //^ &Struct<'_, Struct<'_, &'a str>>
    """, WITH_LIFETIMES)

    fun `test no infinite recursion on 'impl Self' 1`() = testType("""
        impl Self {}
           //^ <unknown>
    """)

    fun `test no infinite recursion on 'impl Self' 2`() = testType("""
        struct S<T>(T);
        impl S<Self> {}
           //^ S<<unknown>>
    """)

    fun `test no infinite recursion on cyclic type`() = testType("""
        type A = B;
        type B = A;
               //^ <unknown>
    """)

    fun `test no infinite recursion on cyclic type array`() = testType("""
        type A = [B; 2];
        type B = A;
               //^ [<unknown>; 2]
    """)

    fun `test no infinite recursion on cyclic type tuple`() = testType("""
        type A = (B, B);
        type B = A;
               //^ (<unknown>, <unknown>)
    """)

    fun `test no infinite recursion on cyclic type fn pointer`() = testType("""
        type A = fn(B);
        type B = A;
               //^ fn(<unknown>)
    """)

    fun `test no infinite recursion on cyclic type with type argument`() = testType("""
        struct S<T>(T);
        type A = S<B>;
        type B = A;
               //^ S<<unknown>>
    """)

    fun `test alias for T`() = testType("""
        type S<T> = T;
        type A = S<u8>;
               //^ u8
    """)

    fun `test render alias name`() = testType("""
        struct S;
        type Foo = S;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name tuple`() = testType("""
        struct S;
        type Foo = (S, S);
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name function`() = testType("""
        type Foo = fn(u32) -> u32;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name trait object`() = testType("""
        trait T {}

        type Foo = dyn T;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name reference`() = testType("""
        type Foo = &'static u32;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name pointer`() = testType("""
        type Foo = *const u32;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias array`() = testType("""
        type Foo = [u32; 3];
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias slice`() = testType("""
        type Foo = [u32];
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias char`() = testType("""
        type Foo = char;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias str`() = testType("""
        type Foo = str;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias bool`() = testType("""
        type Foo = bool;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias integer`() = testType("""
        type Foo = u32;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias float`() = testType("""
        type Foo = f32;
        type Bar = Foo;
                 //^ Foo
    """, WITH_ALIAS_NAMES)

    fun `test render alias name with generics`() = testType("""
        struct S<A, B>(A, B);
        type Foo<T> = S<T, u8>;
        type Bar = Foo<i32>;
                 //^ Foo<i32>
    """, WITH_ALIAS_NAMES)

    fun `test primitive when there is a mod with the same name`() = testType("""
        mod u64 {}
        type T = u64;
                 //^ u64
    """)

    fun `test associated type with name f64`() = testType("""
        trait Trait { type f64; }
        struct S;
        impl Trait for S {
            type f64 = ();
        }
        type A = <S as Trait>::f64;
                             //^ <S as Trait>::f64
    """)

    fun `test unresolved associated type with name f64`() = testType("""
        trait Trait {}
        struct S;
        impl Trait for S {}
        type A = <S as Trait>::f64;
                             //^ <unknown>
    """)

    fun `test no stack overflow when dyn trait used as a Self type in where clause`() = testType("""
        trait Foo<T = ()> {}
        trait Bar {}
        fn foo<T>() where dyn Foo: Bar {}
                        //^ dyn Foo<()>
    """)

    fun `test mixed type and const arguments`() = testType("""
        struct A1;
        const B1: i32 = 1;
        struct C1;

        struct Foo<A, const B: i32, C>(A, C);

        type T = Foo<A1, B1, C1>;
               //^ Foo<A1, 1, C1>
    """)

    fun `test normalizable Self-related associated type path`() = testType("""
        struct S<T>(T);
        trait Foo<T> {
            type Item;
            fn foo(&self) -> Self::Item;
        }
        impl<T, C, R> Foo<T> for S<C> where C: Foo<R>
        {
            type Item = C::Item;
            fn foo(&self) -> Self::Item { todo!() }
        }                        //^ <C as Foo<R>>::Item
    """, normalize = true)

    fun `test normalizable associated type path with nested obligations`() = testType("""
        struct W<T>(T);
        trait Foo { type Item; }
        impl<A, B> Foo for W<A> where A: Foo<Item=B> { type Item = B; }
        struct S;
        struct X;
        impl Foo for S {
            type Item = X;
        }
        type T = <W<S> as Foo>::Item;
                              //^ X
    """, normalize = true)

    fun `test macro type`() = testType("""
        struct Foo<T>(T);
        struct Bar;
        macro_rules! foo {
            () => { Foo<Bar> }
        }
        type T = (foo!(), foo!());
               //^ (Foo<Bar>, Foo<Bar>)
    """)

    fun `test incorrect Self type in macro in impl self type`() = testType("""
        struct Foo<T>(T);
        macro_rules! foo {
            () => { Self }
        }
        impl Foo<(foo!(), foo!())> {}
           //^ Foo<(<unknown>, <unknown>)>
    """)

    fun `test recursive associated type projection`() = testType("""
        trait Trait<A> {
            type Item;
        }
        type T = <T as Trait<T>>::Item;
               //^ <<unknown> as Trait<<unknown>>>::Item
    """)

    fun `test default type argument refers to another type parameter 1`() = testType("""
        struct Foo;
        struct Bar<A = Foo, B = A>(A, B);
        type T = Bar;
               //^ Bar<Foo, Foo>
    """)

    fun `test default type argument refers to another type parameter 2`() = testType("""
        struct Foo;
        struct Bar<A = Foo, B = A>(A, B);
        struct Baz;
        type T = Bar<Baz>;
               //^ Bar<Baz, Baz>
    """)

    fun `test default const argument refers to another const parameter 1`() = testType("""
        struct Bar<const A: i32 = 1, const B: i32 = A>();
        type T = Bar;
               //^ Bar<1, 1>
    """)

    fun `test default const argument refers to another const parameter 2`() = testType("""
        struct Bar<const A: i32 = 1, const B: i32 = A>();
        type T = Bar<2>;
               //^ Bar<2, 2>
    """)

    /**
     * Checks the type of the element in [code] pointed to by `//^` marker.
     */
    private fun testType(
        @Language("Rust") code: String,
        renderMode: RenderMode = DEFAULT,
        normalize: Boolean = false
    ) {
        InlineFile(code)
        val (typeAtCaret, expectedType) = findElementAndDataInEditor<RsTypeReference>()

        checkType(normalize, typeAtCaret, renderMode, expectedType)

        // Additionally test RsPsiSubstitution and PsiSubstitutingPsiRenderer
        if (typeAtCaret is RsPathType && !typeAtCaret.rawType.containsTyOfClass(TyUnknown.javaClass)) {
            val path = typeAtCaret.path
            val resolved = path.reference?.resolve()
            if (resolved is RsGenericDeclaration) {
                val psiSubst = pathPsiSubst(path, resolved)
                val renderedType = PsiSubstitutingPsiRenderer(PsiRenderingOptions(shortPaths = false), listOf(psiSubst))
                    .renderTypeReference(typeAtCaret)
                val reconstructedType = RsPsiFactory(project)
                    .createType(renderedType)
                    .apply { setContext(typeAtCaret) }

                checkType(normalize, reconstructedType, renderMode, expectedType)
            }
        }
    }

    private fun checkType(
        normalize: Boolean,
        typeAtCaret: RsTypeReference,
        renderMode: RenderMode,
        expectedType: String
    ) {
        val ty = if (normalize) {
            typeAtCaret.normType
        } else {
            typeAtCaret.rawType
        }
        val renderedTy = when (renderMode) {
            DEFAULT -> ty.render(useAliasNames = false, skipUnchangedDefaultGenericArguments = false)
            WITH_LIFETIMES -> ty.renderInsertionSafe(includeLifetimeArguments = true, skipUnchangedDefaultGenericArguments = false)
            WITH_ALIAS_NAMES -> ty.render(useAliasNames = true, skipUnchangedDefaultGenericArguments = false)
        }
        check(renderedTy == expectedType) {
            "$renderedTy != $expectedType"
        }
    }

    private enum class RenderMode {
        DEFAULT, WITH_LIFETIMES, WITH_ALIAS_NAMES
    }
}
