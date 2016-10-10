package org.rust.lang.core.resolve

class RustResolveTestCase : RustResolveTestCaseBase() {

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
            if let Some(x) = f() {
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

    fun testStructField() = checkByCode("""
        struct S { foo: i32 }
                  //X

        fn main() {
            let _ = S { foo: 92 };
                       //^
        }
    """)

    fun testEnumField() = checkByCode("""
        enum E { X { foo: i32 } }
                     //X
        fn main() {
            let _ = E::X { foo: 92 };
                          //^
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

    fun testImplSelfType() = checkByCode("""
        struct DumbIterator<'a> { data: &'a [u8], }
                 //X

        impl<'a> Iterator for DumbIterator<'a> {
            type Item = &'a [u8];

            fn next(&mut self) -> Option<Self::Item> { None }
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

    fun testWrongSuper() = checkByCode("""
        fn foo() {}

        mod inner {
            fn main() {
                ::inner::super::foo();
                               //^ unresolved
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

    fun testCircularMod() = checkByCode("""
        use baz::bar;
               //^ unresolved

        // This "self declaration" should not resolve
        // but it once caused a stack overflow in the resolve.
        mod circular_mod;
    """)
}

