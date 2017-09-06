/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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

    fun `test issue 1138`() = checkByCode("""
        mod foo {
            mod inner { pub fn inner() {} }
                                //X
            pub use self::inner::inner;
        }

        mod bar { pub use foo::inner; }
        use bar::inner;

        fn f() { inner(); }
                 //^
    """)

    fun `test constructor`() = checkByCode("""
        struct Foo {}
              //X
        fn Foo() -> Foo { Foo {}}
                         //^
    """)

    fun `test assoc namespaces 1`() = checkByCode("""
        trait Foo {
            type X;
               //X
            const X: Self::X;
        }
        fn foo<T: Foo>() {
            let _: T::X = T::X;
        }           //^
    """)

    fun `test assoc namespaces 2`() = checkByCode("""
        trait Foo {
            type X;
            const X: Self::X;
        }       //X
        fn foo<T: Foo>() {
            let _: T::X = T::X;
        }                  //^
    """)

}
