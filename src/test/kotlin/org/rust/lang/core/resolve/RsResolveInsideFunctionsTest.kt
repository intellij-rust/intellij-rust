/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsResolveInsideFunctionsTest : RsResolveTestBase() {

    fun `test local item in current scope`() = checkByCode("""
        fn main() {
            foo();
          //^
            fn foo() {}
        }    //X
    """)

    fun `test local item in parent scope`() = checkByCode("""
        fn main() {
            if true {
                foo();
            } //^
            fn foo() {}
        }    //X
    """)

    fun `test local import in current scope`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }        //X
        fn main() {
            use mod1::foo;
            foo();
        } //^
    """)

    fun `test local import in parent scope`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }        //X
        fn main() {
            use mod1::foo;
            if true {
                foo();
            } //^
        }
    """)

    fun `test local import to super module`() = checkByCode("""
        fn foo() {}
         //X
        mod mod1 {
            fn main() {
                use super::foo;
                foo();
            } //^
        }
    """)

    fun `test local import with 'self' 1`() = checkByCode("""
        fn main() {
            use self::mod1::foo;
            foo();
        } //^
        mod mod1 {
            pub fn foo() {}
        }        //X
    """)

    fun `test local import with 'self' 2`() = checkByCode("""
        mod inner {
            fn main() {
                use self::mod1::foo;
                foo();
            } //^
            mod mod1 {
                pub fn foo() {}
            }        //X
        }
    """)

    fun `test local import with alias`() = checkByCode("""
        fn main() {
            use mod1::foo as bar;
            bar();
        } //^
        mod mod1 {
            pub fn foo() {}
        }        //X
    """)

    fun `test local import with use group`() = checkByCode("""
        fn main() {
            use mod1::{foo1, foo2};
            foo1();
        } //^
        mod mod1 {
            pub fn foo1() {}
        }        //X
    """)

    fun `test local item vs top-level item`() = checkByCode("""
        fn foo() {}
        fn main() {
            foo();
          //^
            fn foo() {}
        }    //X
    """)

    fun `test local item vs local glob import`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        fn main() {
            use mod1::*;
            foo();
          //^
            fn foo() {}
        }    //X
    """)

    fun `test local imports in current vs parent scopes`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        mod mod2 {
            pub fn foo() {}
        }        //X
        fn main() {
            use mod1::foo;
            if true {
                use mod2::foo;
                foo();
            } //^
        }
    """)

    fun `test local imports in parent vs grandparent scopes`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        mod mod2 {
            pub fn foo() {}
        }        //X
        fn main() {
            use mod1::foo;
            if true {
                use mod2::foo;
                if true {
                    foo();
                } //^
            }
        }
    """)

    fun `test glob import in current scope vs named import in parent scope`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        mod mod2 {
            pub fn foo() {}
        }        //X
        fn main() {
            use mod1::foo;
            if true {
                use mod2::*;
                foo();
            } //^
        }
    """)

    fun `test local parameter vs local import`() = checkByCode("""
        mod mod1 {
            pub const foo: i32 = 0;
        }           //X
        fn func(foo: i32) {
            use mod1::foo;
            foo;
        } //^
    """)

    fun `test local variable vs local named import`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        fn main() {
            use mod1::foo;
            let foo = || {};
              //X
            foo();
        } //^
    """)

    fun `test local variable vs local glob import`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }
        fn main() {
            use mod1::*;
            let foo = || {};
              //X
            foo();
        } //^
    """)

    fun `test local enum 1`() = checkByCode("""
        fn main() {
            enum E { A, B }
               //X
            E::A;
        } //^
    """)

    fun `test local enum 2`() = checkByCode("""
        fn main() {
            enum E { A, B }
                   //X
            E::A;
        }    //^
    """)

    fun `test local named import from local enum`() = expect<IllegalStateException> {
    checkByCode("""
        fn main() {
            enum E { A, B }
                   //X
            use E::A;
            A;
        } //^
    """)
    }

    fun `test local glob import from local enum`() = expect<IllegalStateException> {
    checkByCode("""
        fn main() {
            enum E { A, B }
                   //X
            use E::*;
            A;
        } //^
    """)
    }

    fun `test resolve of local mod`() = checkByCode("""
        fn main() {
            mod inner {
              //X
                pub fn foo() {}
            }
            inner::foo();
        } //^
    """)

    fun `test local macro`() = checkByCode("""
        fn main() {
            macro_rules! foo { () => {} }
                       //X
            foo!();
        } //^
    """)

    fun `test local macro 2 in current scope`() = checkByCode("""
        fn main() {
            foo!();
          //^
            macro foo() {}
        }       //X
    """)

    fun `test local macro 2 in parent scope`() = checkByCode("""
        fn main() {
            if true {
                foo!();
            } //^
            macro foo() {}
        }       //X
    """)

    fun `test macro from local import 1`() = checkByCode("""
        mod mod1 {
            pub macro foo() {}
        }           //X
        fn main() {
            use mod1::foo;
            foo!();
        } //^
    """)

    fun `test macro from local import 2`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () => {}; }
                   //X
    //- main.rs
        fn main() {
            use test_package::foo;
            foo!();
        } //^ lib.rs
    """)

    fun `test import expanded from local macro`() = checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }        //X
        fn main() {
            macro_rules! gen {
                () => { use mod1::foo; }
            }
            gen!();
            foo();
        } //^
    """)

    fun `test import expanded from local macro 2`() = expect<IllegalStateException> {
    checkByCode("""
        mod mod1 {
            pub fn foo() {}
        }        //X
        fn main() {
            macro gen() {
                use mod1::foo;
            }
            gen!();
            foo();
        } //^
    """)
    }

    fun `test macro expanded to $crate import`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! gen {
            () => { use $ crate::foo; }
        }
        pub fn foo() {}
             //X
    //- main.rs
        fn main() {
            test_package::gen!();
            foo();
        } //^ lib.rs
    """)

    fun `test macro expanded to $crate import inside block`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! gen {
            () => {
                {
                    use $ crate::Foo;
                    Foo
                }
            };
        }
        pub struct Foo;
        impl Foo {
            pub fn func(&self) {}
        }        //X
    //- main.rs
        fn main() {
            let foo = test_package::gen!();
            foo.func();
        }     //^ lib.rs
    """)
}
