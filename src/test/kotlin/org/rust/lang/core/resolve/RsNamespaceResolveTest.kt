/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsNamespaceResolveTest : RsResolveTestBase() {
    fun `test mod and fn`() = checkByCode("""
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

    fun `test mod fn inner`() = checkByCode("""
        mod m { fn bar() {} }
                  //X

        fn m() { }

        fn main() { let _ = m::bar(); }
                              //^
    """)

    fun `test mod fn inner inner`() = checkByCode("""
        mod outer {
            mod m { fn bar() {} }
                      //X

            fn m() { }
        }

        fn main() { let _ = outer::m::bar(); }
                                     //^
    """)

    fun `test type and const`() = checkByCode("""
        struct T { }
             //X
        const T: i32 = 0;

        fn main() {
            let _: T = T { };
                 //^
        }
    """)

    fun `test fn struct`() = checkByCode("""
        struct P { }
             //X
        fn P() -> P { }
                //^
    """)

    fun `test static is not type`() = checkByCode("""
        static S: u8  = 0;
        fn main() {
            let _: S = unimplemented!();
                 //^ unresolved
        }
    """)

    fun `test path`() = checkByCode("""
        mod m {
            fn foo() {}
        }

        fn main() {
            let _: m::foo = unimplemented!();
                     //^ unresolved
        }
    """)

    fun `test use fn`() = checkByCode("""
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

    fun `test use mod`() = checkByCode("""
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

    fun `test use mod glob`() = checkByCode("""
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

    fun `test use fn glob`() = checkByCode("""
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
