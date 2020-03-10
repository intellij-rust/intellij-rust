/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

class RsStubOnlyResolveTest : RsResolveTestBase() {
    fun `test child mod`() = stubOnlyResolve("""
    //- main.rs
        mod child;

        fn main() {
            child::foo();
                  //^ child.rs
        }

    //- child.rs
        pub fn foo() {}
    """)

    fun `test nested child mod`() = stubOnlyResolve("""
    //- main.rs
        mod inner {
            pub mod child;
        }

        fn main() {
            inner::child::foo();
                         //^ inner/child.rs
        }

    //- inner/child.rs
        fn foo() {}
    """)

    fun `test mod decl`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
           //^ foo.rs

        fn main() {}
    //- foo.rs

        // Empty file
    """)

    fun `test mod decl 2`() = stubOnlyResolve("""
    //- foo/mod.rs
        use bar::Bar;
                //^ bar.rs

    //- main.rs
        mod bar;
        mod foo;

        fn main() {}

    //- bar.rs
        struct Bar {}
    """)

    fun `test mod decl path`() = stubOnlyResolve("""
    //- main.rs
        #[path = "bar/baz/foo.rs"]
        mod foo;
            //^ bar/baz/foo.rs

        fn main() {}
    //- bar/baz/foo.rs
        fn quux() {}
    """)

    fun `test module path in mod rs`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo/mod.rs
        #[path="baz.rs"]
        mod bar;

        fn foo() {
            self::bar::baz();
                      //^ foo/baz.rs
        }
    //- foo/baz.rs
        pub fn baz() {}
    """, NameResolutionTestmarks.modDeclExplicitPathInNonInlineModule)

    fun `test module path in non crate root`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo.rs
        #[path="baz.rs"]
        mod bar;

        fn foo() {
            self::bar::baz();
                      //^ baz.rs
        }
    //- baz.rs
        pub fn baz() {}
    """, NameResolutionTestmarks.modDeclExplicitPathInNonInlineModule)


    fun `test invalid path`() = stubOnlyResolve("""
    //- main.rs
        use foo::bar;
                //^ unresolved

        fn main() {}
    //- foo.rs
        #[path] mod bar;
    """)

    fun `test mod decl path super`() = stubOnlyResolve("""
    //- bar/baz/quux.rs
        fn quux() {
            super::main();
        }          //^ main.rs

    //- main.rs
        #[path = "bar/baz/quux.rs"]
        mod foo;

        fn main(){}
    """)

    fun `test mod relative`() = stubOnlyResolve("""
    //- main.rs
        mod sub;

        fn main() {
            sub::foobar::quux();
        }               //^ foo.rs

    //- sub.rs
        #[path="./foo.rs"]
        pub mod foobar;

    //- foo.rs
        fn quux() {}
    """)

    fun `test mod relative 2`() = stubOnlyResolve("""
    //- main.rs
        mod sub;

        fn main() {
            sub::foobar::quux();
        }               //^ foo.rs

    //- sub/mod.rs
        #[path="../foo.rs"]
        pub mod foobar;

    //- foo.rs
        pub fn quux() {}
    """)

    fun `test resolve explicit mod path mod rs`() = stubOnlyResolve("""
    //- main.rs
        #[path = "sub/mod.rs"]
        mod foo;

        fn quux() {}
    //- sub/mod.rs
        fn foo() {
            ::quux();
            //^ main.rs
       }
    """)

    fun `test resolve explicit mod path mod rs 2`() = stubOnlyResolve("""
    //- main.rs
        #[path = "sub/bar/mod.rs"]
        mod foo;

        fn quux() {}
    //- sub/bar/mod.rs
        fn foo() {
            ::quux();
            //^ main.rs
       }
    """)

    fun `test resolve explicit mod path mod rs windows path separator`() = stubOnlyResolve("""
    //- main.rs
        #[path = "sub\\bar\\mod.rs"]
        mod foo;

        fn quux() {}
    //- sub/bar/mod.rs
        fn foo() {
            ::quux();
            //^ main.rs
       }
    """)

    fun `test module path`() = stubOnlyResolve("""
    //- main.rs
        #[path="aaa"]
        mod foo {
            mod bar;
        }

        fn quux() {}
    //- aaa/bar.rs
        fn foo() {
            ::quux();
            //^ main.rs
        }
    """)

    fun `test module path 2`() = stubOnlyResolve("""
    //- main.rs
        #[path="aaa/bbb"]
        mod foo {
            mod bar;
        }

        fn quux() {}
    //- aaa/bbb/bar.rs
        fn foo() {
            ::quux();
            //^ main.rs
        }
    """)

    fun `test module path 3`() = stubOnlyResolve("""
    //- main.rs
        #[path="aaa/bbb"]
        mod foo {
            #[path="ccc.rs"]
            mod bar;
        }

        fn quux() {}
    //- aaa/bbb/ccc.rs
        fn foo() {
            ::quux();
            //^ main.rs
        }
    """)

    fun `test empty module path`() = stubOnlyResolve("""
    //- main.rs
        #[path=""]
        mod foo {
            #[path="bbb.rs"]
            mod bar;
        }

        fn quux() {}
    //- bbb.rs
        fn foo() {
            ::quux();
            //^ main.rs
        }
    """)

    fun `test relative module path`() = stubOnlyResolve("""
    //- main.rs
        #[path="./aaa"]
        mod foo {
            mod bar;
        }

        fn quux() {}

    //- aaa/bar.rs
        fn foo() {
            ::quux();
            //^ main.rs
        }
    """)

    fun `test path inside inline module in crate root`() = stubOnlyResolve("""
    //- main.rs
        mod foo {
            #[path="baz.rs"]
            pub mod bar;
        }

        fn main() {
            self::foo::bar::foo();
                           //^ foo/baz.rs
        }
    //- foo/baz.rs
        fn foo() {}
    """, NameResolutionTestmarks.modDeclExplicitPathInInlineModule)

    fun `test path inside inline module in mod rs`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo/mod.rs
        mod bar {
            #[path="qwe.rs"]
            pub mod baz;
        }

        fn foo() {
            self::bar::baz::baz();
                           //^ foo/bar/qwe.rs
        }
    //- foo/bar/qwe.rs
        fn baz() {}
    """, NameResolutionTestmarks.modDeclExplicitPathInInlineModule)

    fun `test path inside inline module in non crate root`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo.rs
        mod bar {
            #[path="qwe.rs"]
            pub mod baz;
        }

        fn foo() {
            self::bar::baz::baz();
                           //^ foo/bar/qwe.rs
        }
    //- foo/bar/qwe.rs
        pub fn baz() {}
    """, NameResolutionTestmarks.modDeclExplicitPathInInlineModule)

    fun `test inline module path in non crate root`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo.rs
        #[path="bar"]
        pub mod bar {
            pub mod baz;
        }

        fn foo() {
            self::bar::baz::baz();
                           //^ bar/baz.rs
        }
    //- bar/baz.rs
        pub fn baz() {}
    """)

    fun `test use from child`() = stubOnlyResolve("""
    //- main.rs
        use child::{foo};
        mod child;

        fn main() {
            foo();
        }  //^ child.rs

    //- child.rs
        pub fn foo() {}
    """)

    fun `test use global path`() {
        stubOnlyResolve("""
        //- foo.rs
            fn main() {
                ::bar::hello();
            }         //^ bar.rs

        //- lib.rs
            mod foo;
            pub mod bar;

        //- bar.rs
            pub fn hello() {}
        """)
    }

    fun `test mod decl wrong path`() = stubOnlyResolve("""
    //- main.rs
        #[path = "foo/bar/baz/rs"]
        mod foo;
           //^ unresolved

        fn main() {}
    """)

    fun `test mod decl cycle`() = stubOnlyResolve("""
    //- foo.rs
        use quux;
            //^ unresolved

        #[path="bar.rs"]
        mod bar;

    //- baz.rs
        #[path="foo.rs"]
        mod foo;

    //- bar.rs
        #[path="baz.rs"]
        mod baz;
    """)

    fun `test function type`() = stubOnlyResolve("""
    //- main.rs
        mod foo;

        struct S { field: u32, }

        fn main() { foo::id(S { field: 92 }).field }
                                             //^ main.rs
    //- foo.rs
        use super::S;
        pub fn id(x: S) -> S { x }
    """)

    fun `test tuple struct`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
        use foo::S;

        fn f(s: S) { s.0.bar() }
                        //^ foo.rs
    //- foo.rs
        struct S(Bar);
        struct Bar;
        impl Bar { fn bar(self) {} }
    """)

    fun `test method call`() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::S;

        fn main() {
            let s: S = S;

            s.foo();
            //^ aux.rs
        }

    //- aux.rs
        pub struct S;

        impl S {
            pub fn foo(&self) { }
        }
    """)

    fun `test method call on enum`() = stubOnlyResolve("""
    //- main.rs
        mod aux;
        use aux::S;

        fn main() {
            let s: S = S::X;

            s.foo();
            //^ aux.rs
        }

    //- aux.rs
        enum S { X }

        impl S {
            fn foo(&self) { }
        }
    """)

    fun `test resolve macro`() = checkByCode("""
        macro_rules! foo_bar { () => () }
        //X
        mod b {
            fn main() {
                foo_bar!();
                //^
            }
        }
    """)

    fun `test resolve macro multi file`() = stubOnlyResolve("""
    //- b.rs
        foo_bar!();
        //^ main.rs
    //- main.rs
        macro_rules! foo_bar { () => () }
        mod b;
        fn main() {}
    """)

    fun `test resolve macro multi file 2`() = stubOnlyResolve("""
    //- b.rs
        macro_rules! foo_bar { () => () }
    //- main.rs
        #[macro_use]
        mod b;
        foo_bar!();
        //^ b.rs
        fn main() {}
    """)

    fun `test resolve macro multi file 3`() = stubOnlyResolve("""
    //- b.rs
        macro_rules! foo_bar { () => () }
        foo_bar!();
        //^ b.rs
    //- main.rs
        #[macro_use]
        mod b;
    """)

    fun `test resolve crate keyword in path to crate root mod`() = stubOnlyResolve("""
    //- main.rs
        mod foo;

        mod bar {
            pub struct Foo;
        }
    //- foo.rs
        use crate::bar::Foo;
            //^ main.rs
    """)

    fun `test raw identifier`() = stubOnlyResolve("""
    //- main.rs
        mod r#match;
        use r#match::bar;

        fn main() {
            bar();
           //^ match.rs
        }
    //- match.rs
        pub fn bar() { println!("Bar"); }
    """)

    fun `test module structure without mod rs 1`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo.rs
        mod bar;
            //^ foo/bar.rs

    //- foo/bar.rs
        pub struct Bar;
    """)

    fun `test module structure without mod rs 2`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
    //- foo/mod.rs
        mod bar;
            //^ foo/bar.rs

    //- foo/bar.rs
        pub struct Bar;
    //- foo/foo/bar.rs
        pub struct Baz;
    """, NameResolutionTestmarks.modRsFile)

    fun `test module structure without mod rs 3`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
           //^ foo.rs
    //- foo.rs
        pub struct Foo;
    //- main/foo.rs
        pub struct Bar;
    """, NameResolutionTestmarks.crateRootModule)

    fun `test scoped resolve inside stubbed function body`() = stubOnlyResolve("""
    //- main.rs
        mod foo;
        fn main() {
            foo::S.baz();
        }        //^ foo.rs
    //- foo.rs
        pub struct S;

        fn foobar() {
            if true {
                impl S {
                    pub fn baz(&self) {}
                }
            } else {
                struct S;
            }
        }
    """)

    fun `test trait impl for const generic 1`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = [0];
            s.foo()
            //^ bar.rs
        }

    //- bar.rs
        pub trait T {
            fn foo(&self);
        }
        
        impl T for [i32; 1] {
            fn foo(&self) {}
        }
    """)

    fun `test trait impl for const generic 2`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = [0, 1];
            s.foo()
            //^ unresolved
        }

    //- bar.rs
        pub trait T {
            fn foo(&self);
        }
        
        impl T for [i32; 1] {
            fn foo(&self) {}
        }
    """)

    fun `test trait impl for const generic 3`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = [0];
            s.foo()
            //^ bar.rs
        }

    //- bar.rs
        pub trait T {
            fn foo(&self);
        }
        
        impl <const N: usize> T for [i32; N] {
            fn foo(&self) {}
        }
    """)

    fun `test trait impl const generic 4`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = bar::S::<0>;
            s.foo()
            //^ bar.rs
        }

    //- bar.rs
        pub struct S<const N: usize>;
        
        pub trait T {
            fn foo(&self);
        }
        
        impl T for S<{ 0 }> {
            fn foo(&self) {}
        }
    """)

    fun `test trait impl const generic 5`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = bar::S::<1>;
            s.foo()
            //^ unresolved
        }

    //- bar.rs
        pub struct S<const N: usize>;
        
        pub trait T {
            fn foo(&self);
        }
        
        impl T for S<{ 0 }> {
            fn foo(&self) {}
        }
    """)

    fun `test trait impl const generic 6`() = stubOnlyResolve("""
    //- main.rs
        #![feature(const_generics)]
        mod bar;
        use bar::T;
        fn main() {
            let s = bar::S::<0>;
            s.foo()
            //^ bar.rs
        }

    //- bar.rs
        pub struct S<const N: usize>;
        
        pub trait T {
            fn foo(&self);
        }
        
        impl <const N: usize> T for S<{ N }> {
            fn foo(&self) {}
        }
    """)
}
