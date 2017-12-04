/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsUseResolveTest : RsResolveTestBase() {

    fun `test view path`() = checkByCode("""
        mod foo {
            use ::bar::hello;
                       //^
        }

        pub mod bar {
            pub fn hello() { }
                   //X
        }
    """)

    fun `test use path`() = checkByCode("""
        fn foo() { }
          //X

        mod inner {
            use foo;

            fn inner() {
                foo();
               //^
            }
        }
    """)

    fun `test child from parent`() = checkByCode("""
        mod foo {
            // This visits `mod foo` twice during resolve
            use foo::bar::baz;

            pub mod bar {
                pub fn baz() {}
                     //X
            }

            fn main() {
                baz();
               //^
            }
        }
    """)

    fun `test path rename`() = checkByCode("""
        fn foo() {}
          //X

        mod inner {
            use foo as bar;

            fn main() {
                bar();
               //^ 3
            }
        }
    """)

    fun `test deep redirection`() = checkByCode("""
        mod foo {
            pub fn a() {}
                 //X
            pub use bar::b as c;
            pub use bar::d as e;
        }

        mod bar {
            pub use foo::a as b;
            pub use foo::c as d;
        }

        fn main() {
            foo::e();
               //^
        }
    """)

    fun `test relative child`() = checkByCode("""
        mod a {
            use self::b::foo;
                        //^

            mod b {
                pub fn foo() {}
                      //X
            }
        }
    """)

    fun `test view path glob ident`() = checkByCode("""
        mod foo {
            use bar::{hello as h};
                      //^
        }

        pub mod bar {
            pub fn hello() { }
                  //X
        }
    """)

    fun `test view path glob self`() = checkByCode("""
        mod foo {
            use bar::{self};
                     //^ 62
        }

        pub mod bar { }
               //X
    """, NameResolutionTestmarks.selfInGroup)

    fun `test view path glob self fn`() = checkByCode("""
        fn f() {}
         //X

        mod foo {
            // This looks strange, but is allowed by the Rust Language
            use f::{self};
                   //^
        }
    """)

    fun `test use glob ident`() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{hello};

            fn main() {
                hello();
                //^
            }
        }
    """)

    fun `test use glob self`() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{self};

            fn main() {
                foo::hello();
                    //^
            }
        }
    """, NameResolutionTestmarks.selfInGroupName)

    fun `test use glob alias`() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use foo::{hello as spam};

            fn main() {
                spam();
               //^
            }
        }
    """)

    fun `test use glob redirection`() = checkByCode("""
        mod foo {
            pub fn a() {}
                 //X
            pub use bar::{b as c, d as e};
        }

        mod bar {
            pub use foo::{a as b, c as d};
        }

        use foo::e;

        fn main() {
            e();
          //^
        }
    """)

    fun `test enum variant`() = checkByCode("""
        mod foo {
            use bar::E::{X};
                       //^
        }

        mod bar {
            pub enum E {
                X
              //X
            }
        }
    """)

    fun `test super part`() = checkByCode("""
        // resolve to the whole file
        //X

        fn foo() {}

        mod inner {
            use super::foo;
               //^
        }
    """)

    fun `test wildcard`() = checkByCode("""
        mod a {
            fn foo() {}
              //X
            fn bar() {}
        }

        mod b {
            use a::*;

            fn main() {
                foo();
                //^
            }
        }
    """)

    fun `test root wildcard 1`() = checkByCode("""
        fn foo() {}
          //X
        mod inner {
            use ::*;
            fn main() { foo(); }
        }              //^
    """)

    fun `test root wildcard 2`() = checkByCode("""
        fn foo() {}
          //X
        mod inner {
            use ::*;
            fn main() { foo(); }
        }              //^
    """)

    fun `test super wildcard with private`() = checkByCode("""
        fn foo() {}
          //X

        #[cfg(test)]
        mod tests {
            use super::*;

            fn test_foo() {
                foo();
               //^
            }
        }
    """)

    fun `test self wildcard private`() = checkByCode("""
        use m::*;
        use self::bar::foo;
                       //^
        mod m {
            pub mod bar { pub fn foo() {} }
        }                       //X
    """)

    fun `test two wildcards`() = checkByCode("""
        mod a {
            pub fn foo() {}
        }

        mod b {
            pub fn bar() {}
                  //X
        }

        mod c {
            use a::*;
            use b::*;

            fn main() {
                bar()
               //^
            }
        }
    """)

    fun `test nested wildcards`() = checkByCode("""
        mod a {
            pub fn foo(){}
                  //X
        }

        mod b {
            pub use a::*;
        }

        mod c {
            use b::*;

            fn main() {
                foo()
               //^
            }
        }
    """)

    fun `test only braces`() = checkByCode("""
        struct Spam;
              //X

        mod foo {
            use {Spam};

            fn main() {
                let _: Spam = unimplemented!();
                      //^
            }
        }
    """)

    fun `test colon braces`() = checkByCode("""
        struct Spam;
              //X

        mod foo {
            use ::{Spam};

            fn main() {
                let _: Spam = unimplemented!();
                      //^
            }
        }
    """)

    fun `test local use`() = checkByCode("""
        mod foo {
            pub struct Bar;
                     //X
        }

        fn main() {
            use foo::Bar;

            let _ = Bar;
                   //^
        }
    """)

    fun `test wildcard priority`() = checkByCode("""
        mod a {
            pub struct S;
        }

        mod b {
            pub struct S;
                     //X
        }

        mod c {
            pub struct S;
        }

        use a::*;
        use b::S;
        use c::*;

        fn main() {
            let _ = S;
                  //^
        }
    """)

    //
    fun `test use self cycle`() = checkByCode("""
         //X
         use self;
            //^
    """)

    fun `test import from self`() = checkByCode("""
         use self::{foo as bar};
         fn foo() {}
           //X
         fn main() { bar() }
                    //^
    """)

    fun `test nested groups`() = checkByCode("""
        mod a { pub mod b { pub mod c { pub fn foo() {} } } }
                                              //X
        use a::{b::{c::foo}};
        fn main() { foo() }
                   //^
    """)

    fun `test no use`() = checkByCode("""
        fn foo() { }

        mod inner {
            fn inner() {
                foo();
               //^ unresolved
            }
        }
    """)

    fun `test cycle`() = checkByCode("""
        // This is a loop: it simultaneously defines and imports `foo`.
        use foo;
        use bar::baz;

        fn main() {
            foo();
           //^ unresolved
        }
    """)

    fun `test use glob cycle`() = checkByCode("""
        mod foo {
            pub use bar::{b as a};
        }

        mod bar {
            pub use foo::{a as b};

            fn main() {
                b();
              //^ unresolved
            }
        }
    """)

    fun `test empty glob list`() = checkByCode("""
        mod foo {
            pub fn f() {}
        }

        mod inner {
            use foo::{};

            fn main() {
                foo::f();
               //^ unresolved
            }
        }
    """)

    fun `test wildcard cycle`() = checkByCode("""
        use inner::*;

        mod inner {
            use super::*;

            fn main() {
                foo()
               //^ unresolved
            }
        }
    """)

    fun `test star imports do not leak`() = checkByCode("""
        fn foo() {}
        mod m {
            use super::*;
        }

        fn bar() {
            m::foo();
             //^ unresolved
        }
    """)

    fun `test circular mod`() = checkByCode("""
        use baz::bar;
               //^ unresolved

        // This "self declaration" should not resolve
        // but it once caused a stack overflow in the resolve.
        mod circular_mod;
    """)

    // This won't actually fail if the resolve is O(N^2) or worse,
    // but this is a helpful test for debugging!
    fun `test quadratic behavior`() = checkByCode("""
        use self::a::*;
        use self::b::*;
        use self::c::*;
        use self::d::*;

        const X1: i32 = 0;
        mod a {
            pub const X2: i32 = ::X1;
        }
        mod b {
            pub const X3: i32 = ::X1;
        }
        mod c {
            pub const X4: i32 = ::X1;
        }
        mod d {
            pub const X5: i32 = ::X1;
                    //X
        }

        const Z: i32 = X5;
                     //^
    """)

    fun `test private glob import`() = checkByCode("""
        mod utils {
            pub enum MyError { SomeError }
        }                       //X
        use utils::*;

        mod bar {
            use super::*;
            fn bar() -> MyError { MyError::SomeError }
        }                                   //^
    """)

    fun `test private reexport`() = checkByCode("""
        mod utils {
            pub enum MyError { SomeError }
        }                       //X
        use utils::MyError;

        mod bar {
            use super::MyError;
            fn bar() -> MyError { MyError::SomeError }
        }                                   //^
    """)

    fun `test can't import methods`() = checkByCode("""
        mod m {
            pub enum E {}

            impl E {
                pub fn foo() {}
            }
        }

        use self::m::E::foo;
                        //^ unresolved
    """)
}
