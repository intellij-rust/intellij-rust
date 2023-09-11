/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.MockRustcVersion
import org.rust.ProjectDescriptor
import org.rust.WithStdlibRustProjectDescriptor

class RsErrorAnnotatorE0277Test : RsAnnotatorTestBase(RsErrorAnnotator::class) {

    fun `test function args should implement Sized trait E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        fn foo1(bar: <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error>) {}
        fn foo2(bar: i32) {}

        trait Trait { type Item; }
        struct StructSized;
        impl Trait for StructSized { type Item = i32; }
        struct StructUnsized;
        impl Trait for StructUnsized { type Item = [i32]; }

        fn foo3(bar: <error><StructUnsized as Trait>::Item</error>) {}
        fn foo4(bar: <StructSized as Trait>::Item) {}
    """)

    fun `test function return type should implement Sized trait E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        fn foo1() -> <error descr="the trait bound `[u8]: std::marker::Sized` is not satisfied [E0277]">[u8]</error> { unimplemented!() }
        fn foo2() -> i32 { unimplemented!() }

        trait Trait { type Item; }
        struct StructSized;
        impl Trait for StructSized { type Item = i32; }
        struct StructUnsized;
        impl Trait for StructUnsized { type Item = [i32]; }

        fn foo3() -> <error><StructUnsized as Trait>::Item</error> { unimplemented!() }
        fn foo4() -> <StructSized as Trait>::Item { unimplemented!() }
    """)

    fun `test type parameter with Sized bound on member function is Sized E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        struct Foo<T>(T);
        impl<T: ?Sized> Foo<T> {
            fn foo() -> T where T: Sized { unimplemented!() }
        }
        impl<T: ?Sized> Foo<T> {
            fn foo() -> T where T: Sized { unimplemented!() }
        }
    """)

    fun `test trait method without body can have arg with 'qSized' type E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        trait Foo {
            fn foo(x: Self);
            fn bar(x: <error>Self</error>) {}
            fn foobar() -> Self;
            fn baz() -> <error>Self</error> { unimplemented!() }
            fn spam<T: ?Sized>(a: T);
            fn eggs<T: ?Sized>(a: <error>T</error>) {}
            fn quux<T>(a: [T]);
            fn quuux<T>(a: <error>[T]</error>) {}
        }
    """)

    fun `test Self type inside trait is sized if have Sized bound E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        trait Foo: Sized {
            fn foo() -> (Self, Self) { unimplemented!() }
        }
        trait Bar where Self: Sized {
            fn foo() -> (Self, Self) { unimplemented!() }
        }
        trait Baz {
            fn foo() -> (Self, Self) where Self: Sized { unimplemented!() }
        }
    """)

    @MockRustcVersion("1.70.0")
    fun `test generic associated type is sized E0277`() = checkErrors("""
        #[lang = "sized"] trait Sized {}
        pub trait Deref { type Target: ?Sized; }
        pub struct Rc<T>(T);
        impl<T> Deref for Rc<T> { type Target = T; }

        trait PointerFamily {
            type Pointer<T>: Deref<Target = T>;
        }
        struct RcFamily;
        impl PointerFamily for RcFamily {
            type Pointer<T> = Rc<T>;
        }
        fn foo<T: PointerFamily>() -> T::Pointer<i32> { // No error here
            todo!()
        }
    """)

    fun `test supertrait is not implemented E0277 simple trait`() = checkErrors("""
        trait A {}
        trait B: A {}

        struct S;

        impl <error descr="the trait bound `S: A` is not satisfied [E0277]">B</error> for S {}
    """)

    fun `test supertrait is not implemented E0277 multiple traits`() = checkErrors("""
        trait A {}
        trait B {}

        trait C: A + B {}

        struct S;

        impl <error descr="the trait bound `S: A` is not satisfied [E0277]"><error descr="the trait bound `S: B` is not satisfied [E0277]">C</error></error> for S {}
    """)

    fun `test supertrait is not implemented E0277 generic supertrait`() = checkErrors("""
        trait A<T> {}
        trait B: A<u32> {}
        trait C<T>: A<T> {}

        struct S1;
        impl <error descr="the trait bound `S1: A<u32>` is not satisfied [E0277]">B</error> for S1 {}

        struct S2;
        impl A<bool> for S2 {}
        impl <error descr="the trait bound `S2: A<u32>` is not satisfied [E0277]">B</error> for S2 {}

        struct S3;
        impl A<bool> for S3 {}
        impl A<u32> for S3 {}
        impl B for S3 {}

        struct S4;
        impl<T> <error descr="the trait bound `S4: A<T>` is not satisfied [E0277]">C<T></error> for S4 {}

        struct S5;
        impl A<u32> for S5 {}
        impl<T> <error descr="the trait bound `S5: A<T>` is not satisfied [E0277]">C<T></error> for S5 {}

        struct S6;
        impl<T> A<T> for S6 {}
        impl<T> C<T> for S6 {}

        struct S7<T>(T);
        impl<T> A<T> for S7<T> {}
        impl<T> C<T> for S7<T> {}

        struct S8;
        impl A<bool> for S8 {}
        impl <error descr="the trait bound `S8: A<u32>` is not satisfied [E0277]">C<u32></error> for S8 {}

        struct S9;
        impl A<u32> for S9 {}
        impl C<u32> for S9 {}
    """)

    fun `test supertrait is not implemented E0277 ignore unknown type`() = checkErrors("""
        trait A<T> {}
        trait B<T>: A<T> {}

        struct S;
        impl B<Foo> for S {}
    """)

    fun `test supertrait is not implemented E0277 self substitution`() = checkErrors("""
        trait Tr1<A=Self> {}
        trait Tr2<A=Self> : Tr1<A> {}

        struct S;
        impl Tr1 for S {}
        impl Tr2 for S {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 2`() = checkErrors("""
        trait Trait<Rhs: ?Sized = Self> {}
        trait Trait2: Trait<Self> {}

        struct X<T>(T);

        impl <T> Trait for X<T> where T: Trait {}
        impl <T> Trait2 for X<T> where T: Trait<T> {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 3`() = checkErrors("""
        trait Trait<Rhs: ?Sized = Self> {}
        trait Trait2: Trait<Self> {}

        struct X<T>(T);

        impl <T: Trait> Trait for X<T> {}
        impl <T> Trait2 for X<T> where T: Trait<T> {}
    """)

    fun `test supertrait is not implemented E0277 self substitution 4`() = checkErrors("""
        trait Foo {}
        trait Baz: Foo {}

        impl<T> Foo for T {}
        impl<T> Baz for T {}
    """)

    fun `test no E0277 for unknown type`() = checkErrors("""
        trait Foo {}
        trait Bar: Foo {}

        impl Bar for S {}
    """)

    fun `test no E0277 for not fully known type`() = checkErrors("""
        trait Foo {}
        trait Bar: Foo {}
        struct S<T>(T);
        impl <T: Foo> Foo for S<T> {}
        impl Bar for S<Q> {}
    """)

    fun `test no E0277 with PartialEq and Eq impls`() = checkErrors("""
        struct Box<T>(T);
        trait PartialEq<Rhs = Self> {}
        trait Eq: PartialEq<Self> {}

        impl<T: PartialEq> PartialEq for Box<T> {}
        impl<T: Eq> Eq for Box<T> {}
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test no E0277 with PartialEq derives and PartialEq impls with generics`() = checkErrors("""
        #[derive(PartialEq)]
        struct House;

        #[derive(PartialEq)]
        struct Village;

        impl PartialEq<Village> for House {
            fn eq(&self, other: &Village) -> bool { todo!() }
        }

        impl PartialEq<House> for Village {
            fn eq(&self, other: &House) -> bool { todo!() }
        }

        impl PartialOrd<Village> for House {
            fn partial_cmp(&self, other: &Village) -> Option<std::cmp::Ordering> { todo!() }
        }

        impl PartialOrd<House> for Village {
            fn partial_cmp(&self, other: &House) -> Option<std::cmp::Ordering> { todo!() }
        }
    """)

    // Issue // https://github.com/intellij-rust/intellij-rust/issues/8786
    fun `test no E0277 when Self-related associated type is mentioned in the parent trait`() = checkErrors("""
        struct S;
        trait Foo<T> {}
        impl Foo<S> for S {}
        trait Bar: Foo<Self::Foo> {
            type Foo;
        }
        impl Bar for S {
            type Foo = S;
        }
    """)
}
