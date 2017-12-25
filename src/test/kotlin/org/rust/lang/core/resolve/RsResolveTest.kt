/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

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

    fun `test nested super`() = checkByCode("""
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

    fun `test enum variant 1`() = checkByCode("""
        enum E { X }
               //X

        fn main() {
            let _ = E::X;
                     //^
        }
    """)

    fun `test enum variant 2`() = checkByCode("""
        enum E { X, Y(X) }
                    //^ unresolved
    """)

    // Enum variants behind an alias are not resolved
    // https://github.com/rust-lang/rust/issues/26264
    // https://github.com/rust-lang/rfcs/issues/2218
    fun `test enum variant with alias`() = checkByCode("""
        enum E { A }
        type T1 = E;
        fn main() {
            let _ = T1::A;
        }             //^ unresolved
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

    // Perhaps this should resolve to the local instead?
    fun `test struct field shorthand`() = checkByCode("""
        struct S { foo: i32, bar: i32 }
                            //X
        fn main() {
            let foo = 92;
            let bar = 62;
            let _ = S { bar, foo };
        }              //^
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

    fun `test pattern constant binding ambiguity`() = checkByCode("""
        const X: i32 = 0;
            //X
        fn foo(x: i32) {
            match x {
                X => 92
            } //^
        }
    """)

    fun `test pattern constant ambiguity 2`() = checkByCode("""
        const NONE: () = ();
             //X

        fn main() {
            match () { NONE => NONE }
        }                     //^
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
}
