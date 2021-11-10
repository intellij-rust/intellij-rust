/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.UseOldResolve
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.stdext.BothEditions

class RsUseResolveTest : RsResolveTestBase() {

    fun `test view path`() = checkByCode("""
        mod foo {
            use crate::bar::hello;
        }                 //^

        pub mod bar {
            pub fn hello() { }
                   //X
        }
    """)

    fun `test use path`() = checkByCode("""
        fn foo() { }
          //X

        mod inner {
            use crate::foo;

            fn inner() {
                foo();
               //^
            }
        }
    """)

    fun `test child from parent`() = checkByCode("""
        mod foo {
            use crate::foo::bar::baz;

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
            use crate::foo as bar;

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
            pub use crate::bar::b as c;
            pub use crate::bar::d as e;
        }

        mod bar {
            pub use crate::foo::a as b;
            pub use crate::foo::c as d;
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
            use crate::bar::{hello as h};
        }                  //^

        pub mod bar {
            pub fn hello() { }
                  //X
        }
    """)

    fun `test view path glob self`() = checkByCode("""
        mod foo {
            use crate::bar::{self};
        }                  //^

        pub mod bar { }
               //X
    """, NameResolutionTestmarks.selfInGroup)

    fun `test view path glob self fn`() = checkByCode("""
        fn f() {}

        mod foo {
            use f::{self};
                   //^ unresolved
        }
    """)

    fun `test use glob ident`() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use crate::foo::{hello};

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
            use crate::foo::{self};

            fn main() {
                foo::hello();
                    //^
            }
        }
    """)

    fun `test use glob alias`() = checkByCode("""
        mod foo {
            pub fn hello() {}
                   //X
        }

        mod bar {
            use crate::foo::{hello as spam};

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
            pub use crate::bar::{b as c, d as e};
        }

        mod bar {
            pub use crate::foo::{a as b, c as d};
        }

        use crate::foo::e;

        fn main() {
            e();
          //^
        }
    """)

    fun `test enum variant`() = checkByCode("""
        mod foo {
            use crate::bar::E::{X};
        }                     //^

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
            pub fn foo() {}
                 //X
            pub fn bar() {}
        }

        mod b {
            use crate::a::*;

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

    // https://github.com/sfackler/rust-openssl/blob/0a0da84f939090f72980c77f40199fc76245d289/openssl-sys/src/asn1.rs#L3
    fun `test wildcard without any path`() = checkByCode("""
        mod inner {
            use *;
            fn foo() {}
             //X
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
            use crate::a::*;
            use crate::b::*;

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
            pub use crate::a::*;
        }

        mod c {
            use crate::b::*;

            fn main() {
                foo()
               //^
            }
        }
    """)

    @MockEdition(Edition.EDITION_2015)
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

    fun `test nested braces`() = checkByCode("""
        mod foo {
            pub fn bar() {}
        }        //X

        use {{foo::{{bar}}}};

        fn main() {
            bar();
        } //^
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

    @UseOldResolve
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

    fun `test private reexport with crate keyword 1`() = checkByCode("""
        mod a {
            pub struct Foo;
        }            //X
        use a::Foo;
        mod b {
            use crate::Foo;

            type T = Foo;
        }          //^
    """)

    fun `test private reexport with crate keyword 2`() = checkByCode("""
        mod root {
            mod a {
                pub struct Foo;
            }            //X
            use self::a::Foo;
            mod b {
                use crate::root::Foo;

                type T = Foo;
            }          //^
        }
    """)

    fun `test private reexport with crate keyword 3`() = checkByCode("""
        mod uses {
            pub use crate::foo::*;
            pub use crate::bar::*;
        }
        mod foo {
            use crate::quux1::Foo;
        }
        mod bar {
            pub use crate::quux2::Foo;
        }
        mod quux1 {
            pub struct Foo;
        }
        mod quux2 {
            pub struct Foo;
        }            //X

        use uses::Foo;
        type T = Foo;
               //^
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

    fun `test wildcard in braces`() = checkByCode("""
        mod m {
            pub enum A { X }
        }              //X

        use m::A::{self, *};

        fn main() {
            let a: A = X;
        }            //^
    """)

    fun `test do not resolve use item inner path to value items 1`() = checkByCode("""
        pub mod foo {
            pub fn bar() {}
                  //X
        }
        pub fn foo() {}

        use self::foo::{bar};
                       //^
    """)

    fun `test do not resolve use item inner path to value items 2`() = checkByCode("""
        pub mod foo {
            pub fn bar() {}
                  //X
        }
        pub const foo: i32 = 123;

        use self::foo::{bar};

        fn main() {
            bar();
        }  //^
    """)

    fun `test cyclic dependent imports`() = checkByCode("""
        mod a {
            pub use crate::b::*;

            pub mod c {
                pub struct Foo;
            }            //X

            type T = self::Foo;
        }                //^

        mod b {
            pub use crate::a::*;
            pub use self::c::*;
        }
    """)

    fun `test underscore import doesn't bring a name into scope`() = checkByCode("""
        pub mod foo {
            pub trait Foo {}
        }
        use foo::Foo as _;
        fn bar(a: &Foo) {}
                  //^ unresolved
    """)

    fun `test unified paths 2018 edition`() = checkByCode("""
        mod foo {
            pub fn bar() {}
        }

        mod baz {
            mod foo {
                pub fn bar() {}
            }         //X
            use foo::bar;
        }          //^
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/3989
    @BothEditions
    fun `test issue #3989 1`() = checkByCode("""
        pub use crate::{bar::*, baz::*};
        mod bar {}
        mod baz { pub struct S; }
                           //X
        mod quux {
            use crate::S;
                     //^
        }
    """)

    // Issue https://github.com/intellij-rust/intellij-rust/issues/3989
    @BothEditions
    fun `test issue #3989 2`() = checkByCode("""
        pub use foo::{bar::*, baz::*};
        mod foo {
            mod bar {}
            mod baz { pub struct S; }
        }                      //X
        mod quux {
            use crate::S;
                     //^
        }
    """)

    fun `test use incomplete path`() = checkByCode("""
        use foo::;
        mod foo {
            pub fn func() {}
        }

        fn func() {}
         //X
        fn main() {
            // here we just check that incomplete path doesn't cause exceptions
            func();
        } //^
    """)

    fun `test complex cyclic chain with glob imports and aliases`() = checkByCode("""
        mod a {
            pub use crate::b::*;
            pub use foo as foo1;
            pub use foo2 as foo3;
            pub use foo4::*;
        }
        mod b {
            pub use crate::a::*;
            pub use foo1 as foo2;
            pub use foo3 as foo4;
            pub mod foo {
                pub struct S;
            }            //X
            type T = S;
        }          //^
    """)

    @UseOldResolve
    fun `test usual import overrides glob import`() = checkByCode("""
        mod foo1 {
            pub mod bar {
                pub fn func() {}
            }
        }

        mod inner {
            pub mod foo2 {
                pub mod bar {
                    pub fn func() {}
                          //X
                }
            }
        }

        use foo1::*;    // adds `bar`
        use bar::func;  // uses `foo1::bar` to resolve func

        use inner::*;
        use foo2::bar;  // unresolved when we resolve `bar::func` (because we haven't processed `use inner::*;` yet)

        fn main() {
            func();
           //^
        }
    """)

    fun `test complex glob imports`() = checkByCode("""
        pub mod inner1 {
            pub mod foo {
                pub fn func() {}
            }        //X

            mod inner2 {
                pub mod foo {}
                pub mod bar {}  // this mod is important
            }
            macro_rules! as_is { ($($ t:tt)*) => { $($ t)* } }
            as_is! {  // import from `inner2` should be resolved after import from `inner1`
                pub use inner2::*;
            }
        }
        pub use inner1::*;
        fn main() {
            foo::func();
        }      //^
    """)

    // based on https://github.com/rust-lang/cargo/blob/875e0123259b0b6299903fe4aea0a12ecde9324f/src/cargo/util/mod.rs#L23
    fun `test import adds same name as existing 1`() = checkByCode("""
        use foo::foo;
        mod foo {
            pub fn foo() {}
        }        //X
        mod inner {
            use crate::foo;
            fn main() {
                foo();
            } //^
        }
    """)

    // based on https://github.com/rust-lang/cargo/blob/875e0123259b0b6299903fe4aea0a12ecde9324f/src/cargo/util/mod.rs#L23
    fun `test import adds same name as existing 2`() = checkByCode("""
        use foo::foo;
        mod foo {
            pub use inner::*;
            pub mod inner {
                pub fn foo() {}
            }        //X
        }
        mod test {
            use crate::foo;
            fn main() {
                foo();
            } //^
        }
    """)

    fun `test two usual imports with same name in different namespaces`() = checkByCode("""
        mod a {
            pub mod foo {}
            fn foo() {}
        }
        mod b {
            pub fn foo() {}
                  //X
        }
        use a::foo;  // type only
        use b::foo;  // value
        fn main() {
            foo();
           //^
        }
    """)
}
