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

    // We resolve mod_decls even if the parent module does not own a directory and mod_decl should not be allowed.
    // This way, we don't need to know the set of crate roots for resolve, which helps indexing.
    // The `mod_decl not allowed here` error is then reported by an annotator.
    fun `test mod decl not own`() = stubOnlyResolve("""
    //- foo.rs
        pub mod bar;

        mod foo {
            pub use super::bar::baz;
                              //^ bar.rs
        }

    //- bar.rs
        pub fn baz() {}

    //- main.rs
        // Empty file
    """)

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
}
