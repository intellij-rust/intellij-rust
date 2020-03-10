/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.type

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
        
    //- mod.rs
        mod foo;
        
        fn bar<const N2: usize>() -> foo::S<{ N2 }> { foo::S }
                                             //^ usize
    """)

    fun `test const generic in trait ref`() = stubOnlyTypeInfer("""
    //- foo.rs
        #![feature(const_generics)]
        
        pub struct S;
        
        pub trait T<const N1: usize> {}
        
    //- mod.rs
        mod foo;
        
        impl <const N2: usize> foo::T<{ N2 }> for foo::S {}
                                       //^ usize
    """)
}
