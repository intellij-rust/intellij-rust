/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsResolveTest : RsResolveTestBase() {

    fun testFunctionArgument() = checkByCode("""
        fn foo(x: i32, y: f64) -> f64 {
                     //X
            y
          //^
        }
    """)

    fun testLocals() = checkByCode("""
        fn foo() {
            let z = 92;
              //X
            z;
          //^
        }
    """)

    fun testShadowing() = checkByCode("""
        fn foo() {
            let z = 92;
            let z = 42;
              //X
            z;
          //^
        }
    """)

    fun testNestedPatterns() = checkByCode("""
        enum E { Variant(i32, i32) }

        struct S { field: E }

        fn main() {
            let S { field: E::Variant(x, _) } = S { field : E::Variant(0, 0) };
                                    //X
            x;
          //^
        }
    """)

    fun testRefPattern() = checkByCode("""
        fn main() {
            let x = 92;
            if let Some(&y) = Some(&x) {
                       //X
                y;
              //^
            }
        }
    """)

    fun testClosure() = checkByCode("""
        fn main() { (0..10).map(|x|  {
                               //X
              x
            //^
        })}
    """)

    fun testMatch() = checkByCode("""
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

    fun testLet() = checkByCode("""
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

    fun testLetCycle1() = checkByCode("""
        fn main() {
            let x = { x };
                    //^ unresolved
        }
    """)

    fun testLetCycle2() = checkByCode("""
        fn main() {
            let x = 92;
              //X
            let x = x;
                  //^
        }
    """)

    fun testLetCycle3() = checkByCode("""
        fn main() {
            if let Some(x) = x { }
                           //^ unresolved
        }
    """)

    fun testIfLet1() = checkByCode("""
        fn main() {
            if let Some(i) = Some(92) {
                      //X
                  i;
                //^
            }
        }
    """)

    fun testIfLet2() = checkByCode("""
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

    fun testWhileLet1() = checkByCode("""
        fn main() {
            while let Some(i) = Some(92) {
                         //X
                i
              //^
            }
        }
    """)

    fun testWhileLet2() = checkByCode("""
        fn main() {
            while let Some(i) = Some(92) { }
            i;
          //^ unresolved
        }
    """)

    fun testFor() = checkByCode("""
        fn main() {
            for x in 0..10 {
              //X
                x;
              //^
            }
        }
    """)

    fun testForNoLeak() = checkByCode("""
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

    fun testTraitMethodArgument() = checkByCode("""
        trait T {
            fn foo(x: i32) {
                 //X
                x;
              //^
            }
        }
    """)

    fun testImplMethodArgument() = checkByCode("""
        impl T {
            fn foo(x: i32) {
                 //X
                x;
              //^
            }
        }
    """)

    fun testStructPatterns1() = checkByCode("""
        #[derive(Default)]
        struct S { foo: i32 }

        fn main() {
            let S {foo} = S::default();
                   //X
            foo;
           //^
        }
    """)

    fun testStructPatterns2() = checkByCode("""
        #[derive(Default)]
        struct S { foo: i32 }

        fn main() {
            let S {foo: bar} = S::default();
                       //X
            bar;
           //^
        }
    """)

    fun testModItems1() = checkByCode("""
        mod m {
            fn main() {
                foo()
              //^
            }

            fn foo() { }
              //X
        }
    """)

    fun testModItems2() = checkByCode("""
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

    fun testModItems3() = checkByCode("""
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

    fun testModItems4() = checkByCode("""
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

    fun testModItems5() = checkByCode("""
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

    fun testNestedModule() = checkByCode("""
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

    fun testSelf() = checkByCode("""
        //X
        fn foo() {}

        fn main() {
            self::foo();
            //^
        }
    """)

    fun testSelf2() = checkByCode("""
        fn foo() {}
          //X

        fn main() {
            self::foo();
                //^
        }
    """)

    fun testSelfIdentifier() = checkByCode("""
        struct S;

        impl S {
            fn foo(&self) -> &S {
                   //X
                self
               //^
            }
        }
    """)

    fun testSuper() = checkByCode("""
        fn foo() {}
           //X

        mod inner {
            fn main() {
                super::foo();
                      //^
            }
        }
    """)

    fun testNestedSuper() = checkByCode("""
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

    fun testFormatPositional() = checkByCode("""
        fn main() {
            let x = 92;
              //X
            println!("{}", x);
                         //^
        }
    """)

    fun testFormatNamed() = checkByCode("""
        fn main() {
            let x = 92;
            let y = 62;
              //X
            print!("{} + {foo}", x, foo = y);
                                        //^
        }
    """)

    fun testEnumVariant1() = checkByCode("""
        enum E { X }
               //X

        fn main() {
            let _ = E::X;
                     //^
        }
    """)

    fun testEnumVariant2() = checkByCode("""
        enum E { X, Y(X) }
                    //^ unresolved
    """)

    fun testLocalFn() = checkByCode("""
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


    fun testEnumField() = checkByCode("""
        enum E { X { foo: i32 } }
                     //X
        fn main() {
            let _ = E::X { foo: 92 };
                          //^
        }
    """)

    fun testEnumStructPattern() = checkByCode("""
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

    fun testNonGlobalPathWithColons() = checkByCode("""
        mod m {
            pub struct Matrix<T> { data: Vec<T> }
                      //X

            pub fn apply(){
                let _ = Matrix::<f32>::fill();
                      //^
            }
        }
    """)

    fun testTypeAlias() = checkByCode("""
        type Foo = usize;
           //X

        trait T { type O; }

        struct S;

        impl T for S { type O = Foo; }
                               //^
    """)

    fun testTrait() = checkByCode("""
        trait Foo { }
             //X

        struct S;
        impl Foo for S { }
            //^
    """)

    fun testForeignFn() = checkByCode("""
        extern "C" { fn foo(); }
                       //X

        fn main() {
            unsafe { foo() }
                   //^
        }
    """)

    fun testForeignStatic() = checkByCode("""
        extern "C" { static FOO: i32; }
                            //X

        fn main() {
            let _ = FOO;
                   //^
        }
    """)

    fun testTraitSelfType() = checkByCode("""
        trait T {
            //X
            fn create() -> Self;
                         //^
        }
    """)

    fun testUnionDef() = checkByCode("""
        union U { f: f64, u: u64 }
            //X
        fn foo(u: U) { }
                //^
    """)

    fun testUnbound() = checkByCode("""
        fn foo() { y }
                 //^ unresolved
    """)

    fun testOrdering() = checkByCode("""
        fn foo() {
            z;
          //^ unresolved
            let z = 92;
        }
    """)

    fun testModBoundary() = checkByCode("""
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

    fun testFollowPath() = checkByCode("""
        fn main() {
            let x = 92;
            foo::x;
               //^ unresolved
        }
    """)

    fun testSelfInStatic() = checkByCode("""
        struct S;

        impl S {
            fn foo() { self }
                      //^ unresolved
        }
    """)

    fun testWrongSelf() = checkByCode("""
        fn foo() {}

        fn main() {
            self::self::foo();
                       //^ unresolved
        }
    """)

    // We resolve this, although this usage of `super` is invalid.
    // Annotator will highlight it as an error.
    fun testWrongSuper() = checkByCode("""
        fn foo() {}
          //X
        mod inner {
            fn main() {
                ::inner::super::foo();
                               //^
            }
        }
    """)

    fun testFunctionIsNotModule() = checkByCode("""
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
}
