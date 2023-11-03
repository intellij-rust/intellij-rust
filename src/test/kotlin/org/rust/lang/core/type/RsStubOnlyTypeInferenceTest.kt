/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

import org.junit.Test

class RsStubOnlyTypeInferenceTest : RsTypificationTestBase() {

    fun `test const expr`() = stubOnlyTypeInfer("""
    //- foo.rs
        const COUNT: usize = 2;
        pub fn foo(a: i32) -> [i32; (2 * COUNT + 3) << (4 / 2)] { unimplemented!() }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo(0);
            x;
          //^ [i32; 28]
        }
    """)

    fun `test const expr 2`() = stubOnlyTypeInfer("""
    //- foo.rs
        const COUNT: usize = 2;
        pub fn foo(a: i32, b: [i32; (2 * COUNT + 3) << (4 / 2)]) { unimplemented!() }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            x;
          //^ ()
        }
    """)

    fun `test const expr in a macro`() = stubOnlyTypeInfer("""
    //- foo.rs
        macro_rules! foo { ($ i:item) => { $ i }; }
        const COUNT: usize = 2;
        foo! { pub fn foo() -> [i32; COUNT] { unimplemented!() } }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            x;
          //^ [i32; 2]
        }
    """)

    fun `test const generic in path (explicit)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<const N1: usize>;

        pub fn foo<const N2: usize>() -> S<{ N2 }> { S }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo::<0>();
            x;
          //^ S<0>
        }
    """)

    fun `test const generic in path (implicit)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        #[derive(Clone, Copy)]
        pub struct S<const N1: usize>;

        pub fn foo<const N2: usize>() -> S<{ N2 }> { S }
        pub fn bar<const N3: usize>(s: S<{ N3 }>) -> S<{ N3 }> { s }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            let y: foo::S<0> = foo::bar(x);
            x;
          //^ S<0>
        }
    """)

    fun `test const generic in path (not inferred)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        #[derive(Clone, Copy)]
        pub struct S<const N1: usize>;

        pub fn foo<const N2: usize>() -> S<{ N2 }> { S }
        pub fn bar<const N3: usize>(s: S<{ N3 }>) -> S<{ N3 }> { s }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo();
            let y = foo::bar(x);
            x;
          //^ S<<unknown>>
        }
    """)

    fun `test const generic in path (wrong arguments order)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<T1, const N1: usize>;

        pub fn foo<T2, const N2: usize>() -> S<{ N2 }, T2> { S }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::foo::<0, usize>();
            x;
          //^ S<T1, <unknown>>
        }
    """)

    fun `test const generic (block expr) 1`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<const N: usize>;

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::S::<{ 0 }>;
            x;
          //^ S<0>
        }
    """)

    fun `test const generic (block expr) 2`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<const N: usize>;

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::S::<{ 1 + 1 }>;
            x;
          //^ S<2>
        }
    """)

    fun `test const generic (block expr) 3`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<const N: usize>;

        pub fn add1<const N: usize>() -> S<{ N + 1 }> { S }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::add1::<1>();
            x;
          //^ S<2>
        }
    """)

    fun `test const generic in method call (explicit)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S1<const N1: usize>;
        pub struct S2<const N2: usize, const M2: usize>;

        impl<const N3: usize> S1<{ N3 }> {
            pub fn foo<const M3: usize>(&self) -> S2<{ N3 }, { M3 }> { S2 }
        }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::S1::<0>.foo::<1usize>();
            x;
          //^ S2<0, 1>
        }
    """)

    fun `test const generic in method call (implicit)`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S1<const N1: usize>;

        #[derive(Clone, Copy)]
        pub struct S2<const N2: usize, const M2: usize>;

        impl<const N3: usize> S1<{ N3 }> {
            pub fn foo<const M3: usize>(&self) -> S2<{ N3 }, { M3 }> { S2 }
        }

        pub fn bar<const N4: usize, const M4: usize>(s: S2<{ N4 }, { M4 }>) -> S2<{ N4 }, { M4 }> { s }

    //- main.rs
        mod foo;

        fn main() {
            let x = foo::S1.foo();
            let y: foo::S2<0, 1> = foo::bar(x);
            x;
          //^ S2<0, 1>
        }
    """)

    fun `test const generic in base type`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<const N1: usize>;

    //- lib.rs
        mod foo;

        fn bar<const N2: usize>() -> foo::S<{ N2 }> { foo::S }
                                             //^ usize
    """)

    fun `test const generic in trait ref`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S;

        pub trait T<const N1: usize> {}

    //- lib.rs
        mod foo;

        impl <const N2: usize> foo::T<{ N2 }> for foo::S {}
                                       //^ usize
    """)

    fun `test const generic rendering order`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]

        pub struct S<
            'a,
            'b,
            'c,
            const A: usize,
            R,
            const B: usize,
            T,
            const C: usize,
            U=u32
        >(&'a [R; A], &'b [T; B], &'c [U; C]);

    //- lib.rs
        mod foo;

        fn main() {
            let s = foo::S(&[1i32], &[2u32], &[3u32]);
            s;
          //^ S<1, i32, 1, u32, 1, u32>
        }
    """)

    fun `test const argument that looks like a type argument`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]
        pub struct S<const N1: usize>;
        pub fn bar<const N2: usize>() -> S<N2> { S }
    //- lib.rs
        mod foo;
        const C: usize = 42;
        fn main() {
            let a = foo::bar::<C>();
            a;
        } //^ S<42>
    """)

    fun `test const with normalizable associated type`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]
        pub struct S<const N1: usize>;
        pub fn bar<const N2: usize>() -> S<N2> { S }
    //- lib.rs
        mod foo;

        struct Struct;
        trait Trait { type Item; }
        impl Trait for Struct { type Item = usize; }

        const C: <Struct as Trait>::Item = 42;
        fn main() {
            let a = foo::bar::<C>();
            a;
        } //^ S<42>
    """)

    fun `test const argument in struct literal`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]
        pub struct S<const N1: usize>;
        pub const C: usize = 42;
    //- lib.rs
        mod foo;
        use foo::*;

        fn main() {
            let a = S::<{ C }> {};
            a;
        } //^ S<42>
    """)

    fun `test const argument in a path qualifier type parameter list`() = stubOnlyTypeInfer("""
    //- foo.rs
        pub struct S<const A: i32>;
        impl<const B: i32> S<B> {
            fn foo() -> Self { todo!() }
        }
        pub const C: i32 = 1;
    //- lib.rs
        mod foo;
        use foo::*;

        fn main() {
            let a = S::<{ C }>::foo();
            a;
        } //^ S<1>
    """)

    fun `test extern function to function pointer coercion`() = stubOnlyTypeInfer("""
    //- foo.rs
        extern {
            pub fn foo(a: i8) -> u64;
        }
    //- lib.rs
        mod foo;
        use foo::*;

        fn main() {
            let f: unsafe fn(i8) -> u64 = foo;
            f;
        } //^ unsafe fn(i8) -> u64
    """)

    fun `test unsafe function to function pointer coercion`() = stubOnlyTypeInfer("""
    //- foo.rs
        pub unsafe fn foo(a: i8) -> u64 { 1u64 }
    //- lib.rs
        mod foo;
        use foo::*;

        fn main() {
            let f: unsafe fn(i8) -> u64 = foo;
            f;
        } //^ unsafe fn(i8) -> u64
    """)

    // TODO RUST-12502
    @Test(expected = IllegalStateException::class)
    fun `test infinite recursion in const evaluation 1`() = stubOnlyTypeInfer("""
    //- foo.rs
        pub struct Uint<const LIMBS: usize> { }

        impl<const LIMBS: usize> Uint<LIMBS> {
            pub const LIMBS: usize = LIMBS;
        }

        pub type U896 = Uint<{ 896 / 64 }>;

        pub trait Tr<T> {}

        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{14}> {}
    //- main.rs
        mod foo;
        use foo::*;

        fn main() {
            let a = foo(U896{});
            a;
        } //^ Uint<7>

        fn foo<A: Tr<B>, B>(a: A) -> B { todo!() }
    """)

    // TODO RUST-12502
    @Test(expected = IllegalStateException::class)
    fun `test infinite recursion in const evaluation 2`() = stubOnlyTypeInfer("""
    //- foo.rs
        pub struct Uint<const LIMBS: usize> { }

        impl<const LIMBS: usize> Uint<LIMBS> {
            pub const LIMBS: usize = LIMBS;
        }

        pub type U896 = Uint<{ 896 / 64 }>;

        pub trait Tr<T> {}

        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS}> {}

        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 1}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 2}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 3}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 4}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 5}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 6}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 7}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 8}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 9}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 10}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 11}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 12}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 13}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 14}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 15}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 16}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 17}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 18}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 19}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 20}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 21}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 22}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 23}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 24}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 25}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 26}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 27}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 28}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 29}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 30}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 31}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 32}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 33}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 34}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 35}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 36}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 37}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 38}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 39}> {}
        impl Tr<Uint<{<U896>::LIMBS / 2}>> for Uint<{<U896>::LIMBS + 40}> {}

    //- main.rs
        mod foo;
        use foo::*;

        fn main() {
            let a = foo(U896{});
            a;
        } //^ Uint<7>

        fn foo<A: Tr<B>, B>(a: A) -> B { todo!() }
    """)

    // Issue RUST-11763. Here we test that there isn't a StackOverflowError.
    // The code is invalid (it does not compile). The inferred types doesn't matter
    fun `test no StackOverflow with infinite recursion in const evaluation`() = stubOnlyTypeInfer("""
    //- foo.rs
        pub struct Uint<const LIMBS: usize> { }

        pub type U128 = Uint<{ 128 }>;

        impl Uint<0> {
            const NEXT: usize = 32;
        }

        impl Uint<32> {
            const NEXT: usize = 64;
        }

        impl Uint<{ <Uint<{32}>>::NEXT }> { // Uint<64>
            const NEXT: usize = 128;
        }

        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
        impl Uint<{ <Uint<{64}>>::NEXT }> { const NEXT: usize = 256; }
    //- main.rs
        mod foo;
        use foo::*;

        fn main() {
            let a = Uint::<{ <Uint::<{ 64 }>>::NEXT }>{};
            a;
        } //^ Uint<<unknown>>
    """)
}
