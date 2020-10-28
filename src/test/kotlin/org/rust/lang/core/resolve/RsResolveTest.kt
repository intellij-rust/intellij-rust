/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.IgnoreInNewResolve
import org.rust.MockEdition
import org.rust.MockRustcVersion
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.ignoreInNewResolve
import org.rust.lang.core.psi.ext.RsFieldDecl

class RsResolveTest : RsResolveTestBase() {

    fun `test function argument`() = checkByCode("""
        fn foo(x: i32, y: f64) -> f64 {
                     //X
            y
          //^
        }
    """)

    fun `test locals`() = checkByCode("""
        fn foo() {
            let z = 92;
              //X
            z;
          //^
        }
    """)

    fun `test shadowing`() = checkByCode("""
        fn foo() {
            let z = 92;
            let z = 42;
              //X
            z;
          //^
        }
    """)

    fun `test nested patterns`() = checkByCode("""
        enum E { Variant(i32, i32) }

        struct S { field: E }

        fn main() {
            let S { field: E::Variant(x, _) } = S { field : E::Variant(0, 0) };
                                    //X
            x;
          //^
        }
    """)

    fun `test ref pattern`() = checkByCode("""
        fn main() {
            let x = 92;
            if let Some(&y) = Some(&x) {
                       //X
                y;
              //^
            }
        }
    """)

    fun `test closure`() = checkByCode("""
        fn main() { (0..10).map(|x|  {
                               //X
              x
            //^
        })}
    """)

    fun `test match`() = checkByCode("""
        fn main() {
            match Some(92) {
                Some(i) => {
                   //X
                    i
                  //^
                }
                _ => 0
            };
        }
    """)

    fun `test match if`() = checkByCode("""
        fn main() {
            let a = true;
              //X
            match Some(92) {
                Some(i) if a => {
                         //^
                    i
                }
                _ => 0
            };
        }
    """)

    fun `test match if 2`() = checkByCode("""
        fn main() {
            match Some(92) {
                Some(i)
                   //X
                        if i < 5 => {
                         //^
                    i
                }
                _ => 0
            };
        }
    """)

    fun `test let`() = checkByCode("""
        fn f(i: i32) -> Option<i32> {}

        fn bar() {
            if let Some(x) = f(42) {
                      //X
                if let Some(y) = f(x) {
                                 //^
                    if let Some(z) = f(y) {}
                }
            }
        }
    """)

    fun `test let cycle 1`() = checkByCode("""
        fn main() {
            let x = { x };
                    //^ unresolved
        }
    """)

    fun `test let cycle 2`() = checkByCode("""
        fn main() {
            let x = 92;
              //X
            let x = x;
                  //^
        }
    """)

    fun `test let cycle 3`() = checkByCode("""
        fn main() {
            if let Some(x) = x { }
                           //^ unresolved
        }
    """)

    fun `test if let 1`() = checkByCode("""
        fn main() {
            if let Some(i) = Some(92) {
                      //X
                  i;
                //^
            }
        }
    """)

    fun `test if let 2`() = checkByCode("""
        fn main() {
            if let Some(i) =  Some(92) { }
            i;
          //^ unresolved
        }
    """)

    fun `test if let with or pattern 1`() = checkByCode("""
        fn foo(x: V) {
            if let V1(v) | V2(v) = x {
                    //X
                v;
              //^
            }
        }
    """)

    fun `test if let with or pattern 2`() = checkByCode("""
        fn foo(x: Option<V>) {
            if let Some(V1(v) | V2(v)) = x {
                         //X
                v;
              //^
            }
        }
    """)

    fun `test if let with or pattern 3`() = checkByCode("""
        fn foo(x: Option<V>) {
            if let Some(L(V1(v) | V2(v)) | R(V1(v) | V2(v))) = x {
                           //X
                v;
              //^
            }
        }
    """)

    fun `test if let else branch`() = checkByCode("""
        fn foo(x: Option<i32>) {
             //X
            if let Some(x) = x {
                x
            } else {
                x
              //^
            }
        }
    """)

    fun `test while let 1`() = checkByCode("""
        fn main() {
            while let Some(i) = Some(92) {
                         //X
                i
              //^
            }
        }
    """)

    fun `test while let 2`() = checkByCode("""
        fn main() {
            while let Some(i) = Some(92) { }
            i;
          //^ unresolved
        }
    """)

    fun `test while let with or pattern 1`() = checkByCode("""
        fn foo(x: V) {
            while let V1(v) | V2(v) = x {
                       //X
                v;
              //^
            }
        }
    """)

    fun `test while let with or pattern 2`() = checkByCode("""
        fn foo(x: Option<V>) {
            while let Some(V1(v) | V2(v)) = x {
                            //X
                v;
              //^
            }
        }
    """)

    fun `test for`() = checkByCode("""
        fn main() {
            for x in 0..10 {
              //X
                x;
              //^
            }
        }
    """)

    fun `test for no leak`() = checkByCode("""
        fn main() {
            for x in x { }
                   //^ unresolved
        }
    """)

    fun `test let overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            let abc = 1u32;
            abc::foo();
               //^
        }
    """)

    fun `test if let overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            if let Some(abc) = Some(1u32) {
                abc::foo();
                   //^
            }
        }
    """)

    fun `test while let overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            while let Some(abc) = Some(1u32) {
                abc::foo();
                   //^
            }
        }
    """)

    fun `test for overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            for abc in Some(1u32).iter() {
                abc::foo();
                   //^
            }
        }
    """)

    fun `test match overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            match Some(1u32) {
                Some(abc) => {
                    abc::foo();
                       //^
                }
                None => {}
            }
        }
    """)

    fun `test lambda overlapping with mod`() = checkByCode("""
        mod abc {
            pub fn foo() {}
                  //X
        }
        fn main() {
            let zz = |abc: u32| {
                abc::foo();
                   //^
            };
        }
    """)

    fun `test trait method argument`() = checkByCode("""
        trait T {
            fn foo(x: i32) {
                 //X
                x;
              //^
            }
        }
    """)

    fun `test impl method argument`() = checkByCode("""
        impl T {
            fn foo(x: i32) {
                 //X
                x;
              //^
            }
        }
    """)

    fun `test struct patterns 1`() = checkByCode("""
        #[derive(Default)]
        struct S { foo: i32 }

        fn main() {
            let S {foo} = S::default();
                   //X
            foo;
           //^
        }
    """)

    fun `test struct patterns 2`() = checkByCode("""
        #[derive(Default)]
        struct S { foo: i32 }

        fn main() {
            let S {foo: bar} = S::default();
                       //X
            bar;
           //^
        }
    """)

    fun `test mod items 1`() = checkByCode("""
        mod m {
            fn main() {
                foo()
              //^
            }

            fn foo() { }
              //X
        }
    """)

    fun `test mod items 2`() = checkByCode("""
        mod bar {
            pub fn foo() {}
                   //X
        }

        mod baz {
            fn boo() {}

            mod foo {
                fn foo() {
                    super::super::bar::foo()
                                      //^
                }
            }
        }
    """)

    fun `test mod items 3`() = checkByCode("""
        mod bar {
            pub fn foo() {}
        }

        mod baz {
            fn boo() {}

            mod foo {
                fn foo() {
                    bar::foo()
                        //^ unresolved
                }
            }
        }
    """)

    fun `test mod items 4`() = checkByCode("""
        mod bar {
            pub fn foo() {}
        }

        mod baz {
            fn boo() {}
              //X

            mod foo {
                fn foo() {
                    super::boo()
                          //^
                }
            }
        }
    """)

    fun `test mod items 5`() = checkByCode("""
        mod bar {
            pub fn foo() {}
        }

        mod baz {
            fn boo() {}

            mod foo {
                fn foo() {
                    boo()
                   //^ unresolved
                }
            }
        }
    """)

    fun `test nested module`() = checkByCode("""
        mod a {
            mod b {
                mod c {
                    pub fn foo() { }
                          //X
                }
            }

            fn main() {
                b::c::foo();
                     //^
            }
        }
    """)

    fun `test self`() = checkByCode("""
        //X
        fn foo() {}

        fn main() {
            self::foo();
            //^
        }
    """)

    fun `test self 2`() = checkByCode("""
        fn foo() {}
          //X

        fn main() {
            self::foo();
                //^
        }
    """)

    fun `test self identifier`() = checkByCode("""
        struct S;

        impl S {
            fn foo(&self) -> &S {
                   //X
                self
               //^
            }
        }
    """)

    fun `test super`() = checkByCode("""
        fn foo() {}
           //X

        mod inner {
            fn main() {
                super::foo();
                      //^
            }
        }
    """)

    fun `test nested super 1`() = checkByCode("""
        mod foo {
            mod bar {
                fn main() {
                    self::super::super::foo()
                                       //^
                }
            }
        }

        fn foo() {}
         //X
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test nested super 2`() = checkByCode("""
        mod foo {
            mod bar {
                use self::super::super::foo;
                fn main() {
                    foo();
                   //^
                }
            }
        }

        fn foo() {}
         //X
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test function and mod with same name`() = checkByCode("""
        mod foo {}

        fn foo() {}
         //X

        fn main() {
            foo();
           //^
        }
    """)

    fun `test format positional`() = checkByCode("""
        fn main() {
            let x = 92;
              //X
            println!("{}", x);
                         //^
        }
    """)

    fun `test format named`() = checkByCode("""
        fn main() {
            let x = 92;
            let y = 62;
              //X
            print!("{} + {foo}", x, foo = y);
                                        //^
        }
    """)

    fun `test await argument`() = checkByCode("""
        fn main() {
            let x = 42;
              //X
            await!(x);
                 //^
        }
    """)

    fun `test dbg argument`() = checkByCode("""
        fn main() {
            let x = 42;
              //X
            dbg!(x);
               //^
        }
    """)

    fun `test enum variant 1`() = checkByCode("""
        enum E { X }
               //X

        fn main() {
            let _ = E::X;
                     //^
        }
    """)

    fun `test enum Self in impl block`() = checkByCode("""
        enum E { X }
               //X

        impl E { fn foo() -> Self { Self::X }}
                                        //^
    """)

    fun `test enum variant 2`() = checkByCode("""
        enum E { X, Y(X) }
                    //^ unresolved
    """)

    fun `test enum variant 3`() = checkByCode("""
        use E::A;

        enum E {
            A { a: u32 },
          //X
        }

        fn main() {
            let e = A { a: 10 };
                  //^
        }
    """)

    fun `test enum variant with alias`() = checkByCode("""
        enum E { A }
               //X
        type T1 = E;
        fn main() {
            let _ = T1::A;
        }             //^
    """)

    fun `test local fn`() = checkByCode("""
        fn main() {
            foo();
           //^

            fn foo() {}
              //X
        }
    """)

    fun `test struct field named`() = checkByCode("""
        struct S { foo: i32 }
                  //X
        fn main() {
            let _ = S { foo: 92 };
        }              //^
    """)

    fun `test struct field positional`() = checkByCodeGeneric<RsFieldDecl>("""
        struct S(i32, i32)
                     //X
        fn main() {
            let _ = S { 1: 92 };
        }             //^
    """)

    fun `test struct field with alias`() = checkByCode("""
        struct S { foo: i32 }
                  //X
        type T1 = S;
        type T2 = T1;
        fn main() {
            let _ = T2 { foo: 92 };
        }              //^
    """)

    fun `test cyclic type aliases`() = checkByCode("""
        type Foo = Bar;
        type Bar = Foo;

        fn main() {
            let x = Foo { foo: 123 };
                        //^ unresolved
        }
    """)

    fun `test struct field Self`() = checkByCode("""
        struct S { foo: i32 }
                  //X
        impl S {
            fn new() -> Self {
                Self {
                    foo: 0
                } //^
            }
        }
    """)

    fun `test type reference inside Self struct literal`() = checkByCode("""
        struct A;
             //X
        struct S { foo: A }
        impl S {
            fn new() -> Self {
                Self {
                    foo: A
                }      //^
            }
        }
    """)

    fun `test type reference struct literal that resolved to non-struct type`() = checkByCode("""
        struct A;
             //X
        trait Trait {}
        fn main() {
            Trait {
                foo: A
            }      //^
        }
    """)

    fun `test Self-related item lookup`() = checkByCode("""
        struct S;
        impl S {
            fn new() -> S { S }
        }    //X

        trait T { fn x(); }
        impl T for S {
            fn x() {
                Self::new();
            }       //^
        }
    """)

    fun `test method of Self type`() = checkByCode("""
        struct S;
        impl S {
            fn foo(a: Self) { a.bar() }
        }                     //^

        trait T { fn bar(&self) {} }
                   //X
        impl T for S {}
    """)

    fun `test struct update syntax`() = checkByCode("""
        struct S {
            f1: u32,
            f2: u8,
        }
        impl S {
            fn new() -> Self {
             //X
                S { f1: 0, f2: 0 }
            }
        }
        fn main() {
            let a = S { f1: 1, ..S::new() };
        }                         //^
    """)

    fun `test struct update syntax Default`() = checkByCode("""
        trait Default {
            fn default() -> Self;
        }
        struct S {
            f1: u32,
            f2: u8,
        }
        impl Default for S {
            fn default() -> Self {
             //X
                S { f1: 0, f2: 0 }
            }
        }
        fn main() {
            let a = S { f1: 1, ..Default::default() };
        }                               //^
    """)

    fun `test enum field`() = checkByCode("""
        enum E { X { foo: i32 } }
                     //X
        fn main() {
            let _ = E::X { foo: 92 };
                          //^
        }
    """)

    fun `test enum struct pattern`() = checkByCode("""
        enum E {
            B { f: i32 }
          //X
        }

        fn process_message(msg: E) {
            match msg {
                E::B { .. } => {}
                 //^
            };
        }
    """)

    fun `test non global path with colons`() = checkByCode("""
        mod m {
            pub struct Matrix<T> { data: Vec<T> }
                      //X

            pub fn apply(){
                let _ = Matrix::<f32>::fill();
                      //^
            }
        }
    """)

    fun `test type alias`() = checkByCode("""
        type Foo = usize;
           //X

        trait T { type O; }

        struct S;

        impl T for S { type O = Foo; }
                               //^
    """)

    fun `test trait`() = checkByCode("""
        trait Foo { }
             //X

        struct S;
        impl Foo for S { }
            //^
    """)

    fun `test foreign fn`() = checkByCode("""
        extern "C" { fn foo(); }
                       //X

        fn main() {
            unsafe { foo() }
                   //^
        }
    """)

    fun `test foreign static`() = checkByCode("""
        extern "C" { static FOO: i32; }
                            //X

        fn main() {
            let _ = FOO;
                   //^
        }
    """)

    fun `test trait Self type`() = checkByCode("""
        trait T {
            //X
            fn create() -> Self;
                         //^
        }
    """)

    fun `test struct Self type`() = checkByCode("""
        pub struct S<'a> {
                 //X
            field: &'a Self
        }             //^
    """)

    fun `test enum Self type`() = checkByCode("""
        pub enum E<'a> {
               //X
            V { field: &'a Self }
        }                //^
    """)

    fun `test union def`() = checkByCode("""
        union U { f: f64, u: u64 }
            //X
        fn foo(u: U) { }
                //^
    """)

    fun `test unbound`() = checkByCode("""
        fn foo() { y }
                 //^ unresolved
    """)

    fun `test ordering`() = checkByCode("""
        fn foo() {
            z;
          //^ unresolved
            let z = 92;
        }
    """)

    fun `test mod boundary`() = checkByCode("""
        mod a {
            fn foo() {}
            mod b {
                fn main() {
                    foo()
                   //^ unresolved
                }
            }
        }
    """)

    fun `test follow path`() = checkByCode("""
        fn main() {
            let x = 92;
            foo::x;
               //^ unresolved
        }
    """)

    fun `test self in static`() = checkByCode("""
        struct S;

        impl S {
            fn foo() { self }
                      //^ unresolved
        }
    """)

    fun `test wrong self`() = checkByCode("""
        fn foo() {}

        fn main() {
            self::self::foo();
                       //^ unresolved
        }
    """)

    // We resolve this, although this usage of `super` is invalid.
    // Annotator will highlight it as an error.
    fun `test wrong super`() = checkByCode("""
        fn foo() {}
          //X
        mod inner {
            fn main() {
                ::inner::super::foo();
                               //^
            }
        }
    """)

    fun `test function is not module`() = checkByCode("""
        fn foo(bar: usize) {}

        fn main() {
            foo::bar // Yep, we used to resolve this!
                //^ unresolved
        }
    """)

    fun `test lifetime in function arguments`() = checkByCode("""
        fn foo<'a>(
              //X
            a: &'a u32) {}
               //^
    """)

    fun `test lifetime in function return type`() = checkByCode("""
        fn foo<'a>()
              //X
            -> &'a u32 {}
               //^
    """)

    fun `test lifetime in struct`() = checkByCode("""
        struct Foo<'a> {
                  //X
            a: &'a u32 }
               //^
    """)

    fun `test lifetime in enum`() = checkByCode("""
        enum Foo<'a> {
                //X
            BAR(&'a u32) }
                //^
    """)

    fun `test lifetime in type alias`() = checkByCode("""
        type Str<'a>
                //X
            = &'a str;
              //^
    """)

    fun `test lifetime in impl`() = checkByCode("""
        struct Foo<'a> { a: &'a str }
        impl<'a>
            //X
            Foo<'a> {}
               //^
    """)

    fun `test lifetime in trait`() = checkByCode("""
        trait Named<'a> {
                   //X
            fn name(&self) -> &'a str;
                              //^
         }
    """)

    fun `test lifetime in fn parameters`() = checkByCode("""
        fn foo<'a>(
              //X
          f: &'a Fn() -> &'a str) {}
                         //^
    """)

    fun `test lifetime in type param bounds`() = checkByCode("""
        fn foo<'a,
              //X
            T: 'a>(a: &'a T) {}
              //^
    """)

    fun `test lifetime in where clause`() = checkByCode("""
        fn foo<'a, T>(a: &'a T)
              //X
            where T: 'a {}
                    //^
    """)

    fun `test lifetime in for lifetimes 1`() = checkByCode("""
        fn foo_func<'a>(a: &'a u32) -> &'a u32 { a }
        const FOO: for<'a> fn(&'a u32)
                      //X
            -> &'a u32 = foo_func;
               //^
    """)

    fun `test lifetime in for lifetimes 2`() = checkByCode("""
        fn foo<F>(f: F) where F: for<'a>
                                    //X
            Fn(&'a i32) {}
               //^
    """)

    fun `test lifetime in for lifetimes 3`() = checkByCode("""
        trait Foo<T> {}
        fn foo<T>(t: T) where for<'a>
                                 //X
                              T: Foo<&'a T> {}
                                     //^
    """)

    fun `test static lifetime unresolved`() = checkByCode("""
        fn foo(name: &'static str) {}
                      //^ unresolved
    """)

    @MockRustcVersion("1.23.0")
    fun `test in-band lifetime unresolved`() = checkByCode("""
        fn foo(
            x: &'a str,
            y: &'a str
               //^ unresolved
        ) {}
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test in-band lifetime resolve`() = checkByCode("""
        #![feature(in_band_lifetimes)]
        fn foo(
            x: &'a str,
               //X
            y: &'a str
               //^
        ) {}
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test in-band lifetime single definition`() = checkByCode("""
        #![feature(in_band_lifetimes)]
        fn foo(
            x: &'a str,
               //X
            y: &'a str
        ) {
            let z: &'a str = unimplemented!();
                   //^
        }
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test in-band lifetime no definition in body`() = checkByCode("""
        #![feature(in_band_lifetimes)]
        fn foo() {
            let z: &'a str = unimplemented!();
                   //^ unresolved
        }
    """)

    @MockRustcVersion("1.23.0-nightly")
    fun `test in-band and explicit lifetimes`() = checkByCode("""
        #![feature(in_band_lifetimes)]
        fn foo<'b>(
            x: &'a str,
            y: &'a str
               //^ unresolved
        ) {}
    """)

    fun `test loop label`() = checkByCode("""
        fn foo() {
            'a: loop {
           //X
                break 'a;
                     //^
            }
        }
    """)

    fun `test while loop label`() = checkByCode("""
        fn foo() {
            'a: while true {
           //X
                continue 'a;
                        //^
            }
        }
    """)

    fun `test for loop label`() = checkByCode("""
        fn foo() {
            'a: for _ in 0..3 {
           //X
                break 'a;
                     //^
            }
        }
    """)

    fun `test for loop label vs lifetime conflict 1`() = checkByCode("""
        fn foo<'a>(a: &'a str) {
            'a: for _ in 0..3 {
           //X
                break 'a;
                     //^
            }
        }
    """)

    fun `test for loop label vs lifetime conflict 2`() = checkByCode("""
        fn foo<'a>(a: &'a str) {
              //X
            'a: for _ in 0..3 {
                let _: &'a str = a;
                       //^
            }
        }
    """)

    fun `test block label`() = checkByCode("""
        fn main() {
            let block_with_label = 'block: {
                                   //X
                if true { break 'block 1; }
                               //^
                3
            };
        }
    """)

    fun `test pattern constant binding ambiguity`() = checkByCode("""
        const X: i32 = 0;
            //X
        fn foo(x: i32) {
            match x {
                X => 92
            };//^
        }
    """)

    fun `test pattern constant ambiguity 2`() = checkByCode("""
        const NONE: () = ();
             //X

        fn main() {
            match () { NONE => NONE }
        }                     //^
    """)

    fun `test pattern binding in let 1`() = checkByCode("""
        struct S { foo: i32 }
                  //X
        fn main() {
            let S { foo } = S { foo: 92 };
                   //^
            let x = foo;
        }
    """)

    fun `test pattern binding in let 2`() = checkByCode("""
        struct S { foo: i32 }
                  //X
        type A = S;
        fn main() {
            let A { foo } = A { foo: 92 };
                   //^
            let x = foo;
        }
    """)

    fun `test match enum path`() = checkByCode("""
        enum Enum { Var1, Var2 }
                  //X
        fn main() {
            match Enum::Var1 {
                Enum::Var1 => {}
                    //^
                _ => {}
            }
        }
    """)

    fun `test associated type binding`() = checkByCode("""
        trait Tr {
            type Item;
        }      //X
        type T = Tr<Item=u8>;
                  //^
    """)

    fun `test inherited associated type binding`() = checkByCode("""
        trait Tr1 {
            type Item;
        }      //X
        trait Tr2: Tr1 {}
        type T = Tr2<Item=u8>;
                   //^
    """)

    fun `test associated type binding in (wrong) non-type context (fn call)`() = checkByCode("""
        fn foo() {}
        fn main () {
            foo::<Item=u8>();
        }       //^ unresolved
    """)

    fun `test associated type binding in (wrong) non-type context (method call)`() = checkByCode("""
        struct S;
        impl S { fn foo(&self) {} }
        fn main () {
            S.foo::<Item=u8>();
        }         //^ unresolved
    """)

    fun `test raw identifier 1`() = checkByCode("""
        fn foo() {
            let r#match = 42;
              //X
            r#match;
          //^
        }
    """)

    fun `test raw identifier 2`() = checkByCode("""
        fn foo() {}
           //X
        fn main() {
            r#foo();
             //^
        }
    """)

    fun `test raw identifier 3`() = checkByCode("""
        struct r#Foo;
               //X
        fn main() {
            let f = Foo;
                   //^
        }
    """)

    fun `test resolve path with crate keyword`() = checkByCode("""
        mod foo {
            pub struct Foo;
                      //X
        }

        use crate::foo::Foo;
                       //^
    """)

    fun `test resolve path with crate keyword 2`() = checkByCode("""
        mod foo {
            pub struct Foo;
                      //X
        }

        fn main() {
            let foo = crate::foo::Foo;
                                 //^
        }
    """)

    fun `test derive serde Serialize`() = checkByCode("""
        #[lang = "serde::Serialize"]
        trait Serialize { fn serialize(&self); }
                           //X
        #[derive(Serialize)]
        struct Foo;

        fn bar(foo: Foo) {
            foo.serialize();
              //^
        }
    """)

    fun `test 'pub (in path)' is crate-relative`() = checkByCode("""
        mod foo {
          //X
            mod bar {
                pub(in foo) fn baz() {}
            }        //^
        }
    """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test 'pub (in incomplete_path)'`() = checkByCode("""
        mod foo {
            mod bar {
                pub(in foo::) fn baz() {}
                               //X
                fn main() {
                    // here we just check that incomplete path doesn't cause exceptions
                    baz();
                } //^
            }
        }
    """)

    fun `test 'pub (self)'`() = checkByCode("""
        mod bar {
          //X
            pub(self) fn baz() {}
        }     //^
    """)

    fun `test 'pub (super)'`() = checkByCode("""
        mod foo {
          //X
            mod bar {
                pub(super) fn baz() {}
            }     //^
        }
    """)

    fun `test 'pub (self)' mod`() = checkByCode("""
        mod foo {
          //X
            pub(self) mod bar {}
        }     //^
    """)

    fun `test extern crate self`() = checkByCode("""
        extern crate self as foo;

        struct Foo;
              //X

        use foo::Foo;
                //^
    """)

    fun `test extern crate self without alias`() = checkByCode("""
        extern crate self;

        struct Foo;
              //X

        use self::Foo;
                //^
    """, ItemResolutionTestmarks.externCrateSelfWithoutAlias.ignoreInNewResolve())

    fun `test const generic in fn`() = checkByCode("""
        fn f<const AAA: usize>() {
                  //X
            AAA;
           //^
        }
    """)

    fun `test const generic in struct`() = checkByCode("""
        struct S<const AAA: usize> {
                      //X
            x: [usize; AAA]
                      //^
        }
    """)

    fun `test const generic in trait`() = checkByCode("""
        trait T<const AAA: usize> {
                     //X
            const BBB: usize = AAA;
                              //^
        }
    """)

    fun `test const generic in enum`() = checkByCode("""
        enum E<const AAA: usize> {
                    //X
            V([usize; AAA]),
                     //^
        }
    """)
}
