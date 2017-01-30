package org.rust.lang.core.resolve

class RsStubOnlyResolveTest : RsResolveTestBase() {
    fun testChildMod() {
        if (is2016_2()) return

        stubOnlyResolve("""
        //- main.rs
            mod child;

            fn main() {
                child::foo();
                      //^ child.rs
            }

        //- child.rs
            pub fn foo() {}
        """)
    }

    fun testNestedChildMod() {
        if (is2016_2()) return

        stubOnlyResolve("""
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
    }

    fun testModDecl() = stubOnlyResolve("""
    //- main.rs
        mod foo;
           //^ foo.rs

        fn main() {}
    //- foo.rs

        // Empty file
    """)

    fun testModDecl2() = stubOnlyResolve("""
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

    fun testModDeclPath() = stubOnlyResolve("""
    //- main.rs
        #[path = "bar/baz/foo.rs"]
        mod foo;
            //^ bar/baz/foo.rs

        fn main() {}
    //- bar/baz/foo.rs
        fn quux() {}
    """)

    fun testModDeclPathSuper() {
        if (is2016_2()) return

        stubOnlyResolve("""
        //- bar/baz/quux.rs
            fn quux() {
                super::main();
            }          //^ main.rs

        //- main.rs
            #[path = "bar/baz/quux.rs"]
            mod foo;

            fn main(){}
        """)
    }

    fun testModRelative() {
        if (is2016_2()) return

        stubOnlyResolve("""
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
    }

    fun testModRelative2() {
        if (is2016_2()) return

        stubOnlyResolve("""
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
    }

    fun testUseFromChild() {
        if (is2016_2()) return

        stubOnlyResolve("""
        //- main.rs
            use child::{foo};
            mod child;

            fn main() {
                foo();
            }  //^ child.rs

        //- child.rs
            pub fn foo() {}
        """)
    }

    fun testUseGlobalPath() {
        if (is2016_2()) return

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
    fun testModDeclNotOwn() = stubOnlyResolve("""
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

    fun testModDeclWrongPath() = stubOnlyResolve("""
    //- main.rs
        #[path = "foo/bar/baz/rs"]
        mod foo;
           //^ unresolved

        fn main() {}
    """)

    fun testModDeclCycle() = stubOnlyResolve("""
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

    fun testFunctionType() {
        if (is2016_2()) return

        stubOnlyResolve("""
        //- main.rs
            mod foo;

            struct S { field: u32, }

            fn main() { foo::id(S { field: 92 }).field }
                                                 //^ main.rs
        //- foo.rs
            use super::S;
            pub fn id(x: S) -> S { x }
        """)
    }

}
