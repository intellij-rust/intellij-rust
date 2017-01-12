package org.rust.lang.core.resolve

class RsNamespaceResolveTest : RsResolveTestBase() {
    fun testModAndFn() = checkByCode("""
        mod test {
           //X
            pub struct Test {
                pub a: u32,
            }
        }

        fn main() {
            let mut test = test::Test { a: 42 };
            let test: test::Test = test; // New immutable binding so test is not accidentally modified
                      //^
            println!("Value: {}", test.a);
        }
    """)

    fun testModFnInner() = checkByCode("""
        mod m { fn bar() {} }
                  //X

        fn m() { }

        fn main() { let _ = m::bar(); }
                              //^
    """)

    fun testModFnInnerInner() = checkByCode("""
        mod outer {
            mod m { fn bar() {} }
                      //X

            fn m() { }
        }

        fn main() { let _ = outer::m::bar(); }
                                     //^
    """)

    fun testTypeAndConst() = checkByCode("""
        struct T { }
             //X
        const T: i32 = 0;

        fn main() {
            let _: T = T { };
                 //^
        }
    """)

    fun testFnStruct() = checkByCode("""
        struct P { }
             //X
        fn P() -> P { }
                //^
    """)

    fun testStaticIsNotType() = checkByCode("""
        static S: u8  = 0;
        fn main() {
            let _: S = unimplemented!();
                 //^ unresolved
        }
    """)

    fun testPath() = checkByCode("""
        mod m {
            fn foo() {}
        }

        fn main() {
            let _: m::foo = unimplemented!();
                     //^ unresolved
        }
    """)

    fun testUseFn() = checkByCode("""
        use m::foo;
        mod m {
            fn foo() {}
              //X
            mod foo { fn bar() {} }

        }

        fn main() {
            foo();
           //^
            foo::bar();

        }
    """)

    fun testUseMod() = checkByCode("""
        use m::foo;
        mod m {
            fn foo() {}

            mod foo { fn bar() {} }
               //X
        }

        fn main() {
            foo();

            foo::bar();
           //^
        }
    """)

    fun testUseModGlob() = checkByCode("""
        use m::{foo};
        mod m {
            fn foo() {}

            mod foo { fn bar() {} }
               //X
        }

        fn main() {
            foo();

            foo::bar();
           //^
        }
    """)

    fun testUseFnGlob() = checkByCode("""
        use m::{foo};
        mod m {
            fn foo() {}
              //X
            mod foo { fn bar() {} }

        }

        fn main() {
            foo();
           //^
            foo::bar();

        }
    """)
}
