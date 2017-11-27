/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.lang.core.types.infer.TypeInferenceMarks

class RsPreciseTraitMatchingTest : RsResolveTestBase() {
    fun `test method in specialized trait impl for struct`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T> { value: T }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S {value: 5u16};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for struct 2`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T1, T2> { value1: T1, value2: T2 }
        impl Tr for S<u8, u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16, u8> {
            fn some_fn(&self) { }
             //X
        }
        impl Tr for S<u8, u16> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16, u16> {
            fn some_fn(&self) { }
        }
        fn main() {
            let v = S {value1: 5u16, value2: 5u8};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for tuple struct`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        struct S<T> (T);
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S (5u16);
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for enum`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        enum S<T> { Var1{value: T}, Var2 }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S::Var1 {value: 5u16};
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized trait impl for tuple enum`() = checkByCode("""
        trait Tr { fn some_fn(&self); }
        enum S<T> { Var1(T), Var2  }
        impl Tr for S<u8> {
            fn some_fn(&self) { }
        }
        impl Tr for S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main() {
            let v = S::Var1 (5u16);
            v.some_fn();
            //^
        }
    """)

    fun `test method in specialized impl for struct`() = checkByCode("""
        struct S<T> { value: T }
        impl S<u8> {
            fn some_fn(&self) { }
        }
        impl S<u16> {
            fn some_fn(&self) { }
             //X
        }
        fn main(v: S<u16>) {
            v.some_fn();
            //^
        }
    """)

    fun `test trait bound satisfied for struct`() = checkByCode("""
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        struct S<T> { value: T }
        impl<T: Bound1> Tr1 for S<T> {}
        impl<T: Bound2> Tr2 for S<T> {}
        struct S0;
        impl Bound2 for S0 {}
        fn main(v: S<S0>) {
            v.some_fn();
            //^
        }
    """, TypeInferenceMarks.methodPickCheckBounds)

    fun `test trait bound satisfied for trait`() = checkByCode("""
        #[lang = "sized"]
        trait Sized {}
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        trait ChildOfBound2 : Bound2 {}
        struct S<T: ?Sized> { value: T }
        impl<T: Bound1 + ?Sized> Tr1 for S<T> { }
        impl<T: Bound2 + ?Sized> Tr2 for S<T> { }
        fn f(v: &S<ChildOfBound2>) {
            v.some_fn();
            //^
        }
    """, TypeInferenceMarks.methodPickCheckBounds)

    fun `test trait bound satisfied for other bound`() = checkByCode("""
        trait Tr1 { fn some_fn(&self) {} }
        trait Tr2 { fn some_fn(&self) {} }
                     //X
        trait Bound1 {}
        trait Bound2 {}
        struct S<T> { value: T }
        impl<T: Bound1> Tr1 for S<T> { }
        impl<T: Bound2> Tr2 for S<T> { }

        struct S1<T> { value: T }
        impl<T: Bound2> S1<T> {
            fn f(&self, t: S<T>) {
                t.some_fn();
                //^
            }
        }
    """, TypeInferenceMarks.methodPickCheckBounds)

    fun `test allow ambiguous trait bounds for postponed selection`() = checkByCode("""
        trait Into<A> { fn into(&self) -> A; }
        trait From<B> { fn from(_: B) -> Self; }
        impl<T, U> Into<U> for T where U: From<T>
        {
            fn into(self) -> U { U::from(self) }
        }    //X

        struct S1;
        struct S2;
        impl From<S1> for S2 { fn from(_: B) -> Self { unimplemented!() }
}
        fn main() {
            let a = (&S1).into();
                        //^
            let _: S2 = a;
        }
    """)

    fun `test method defined in out of scope trait 1`() = checkByCode("""
        struct S;

        mod a {
            use super::S;
            pub trait A { fn foo(&self){} }
                           //X
            impl A for S {}
        }

        mod b {
            use super::S;
            pub trait B { fn foo(&self){} }
            impl B for S {}
        }

        fn main() {
            use a::A;
            S.foo();
        }   //^
    """, TypeInferenceMarks.methodPickTraitScope)

    fun `test method defined in out of scope trait 2`() = checkByCode("""
        struct S;

        mod a {
            use super::S;
            pub trait A { fn foo(&self){} }
            impl A for S {}
        }

        mod b {
            use super::S;
            pub trait B { fn foo(&self){} }
                           //X
            impl B for S {}
        }

        fn main() {
            use b::B;
            S.foo();
        }   //^
    """, TypeInferenceMarks.methodPickTraitScope)

    fun `test specialization simple`() = checkByCode("""
        trait Tr { fn foo(&self); }
        struct S;
        impl<T> Tr for T { fn foo(&self) {} }
        impl Tr for S { fn foo(&self) {} }
                         //X
        fn main() {
            S.foo();
        }   //^
    """, TypeInferenceMarks.traitSelectionSpecialization)
}
