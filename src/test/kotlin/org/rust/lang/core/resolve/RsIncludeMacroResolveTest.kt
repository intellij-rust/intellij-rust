/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.MockEdition
import org.rust.UseNewResolve
import org.rust.cargo.project.workspace.CargoWorkspace

class RsIncludeMacroResolveTest : RsResolveTestBase() {

    fun `test resolve struct to included file`() = checkResolve("""
    //- main.rs
        include!("foo.rs");
        fn main() {
            println("{:?}", Foo);
                           //^ foo.rs
        }
    //- foo.rs
        #[derive(Debug)]
        struct Foo;
    """)

    fun `test resolve method to included file`() = checkResolve("""
    //- main.rs
        include!("foo.rs");
        fn main() {
            Foo.foo();
                //^ foo.rs
        }
    //- foo.rs
        struct Foo;
        impl Foo {
            fn foo(&self) {}
        }
    """)

    fun `test resolve function from included file`() = checkResolve("""
    //- lib.rs
        include!("foo.rs");
        fn bar() {}
    //- foo.rs
        pub fn foo() {
            bar();
           //^ lib.rs
        }
    """)

    fun `test resolve method from included file`() = checkResolve("""
    //- lib.rs
        include!("foo.rs");
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }
    //- foo.rs
        pub fn foo() {
            Bar.bar();
               //^ lib.rs
        }
    """)

    fun `test resolve to correct included file`() = checkResolve("""
    //- main.rs
        include!("foo/baz.rs");

        fn foo(f: Foo) {}
                 //^ foo/baz.rs
    //- lib.rs
        include!("bar/baz.rs");
    //- foo/baz.rs
        struct Foo;
    //- bar/baz.rs
        struct Foo;
    """)

    fun `test resolve struct from module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::Baz) {}
                         //^ foo/baz.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub struct Baz;
    """)

    fun `test resolve struct from module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::Baz) {}
                         //^ foo/baz/mod.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz/mod.rs
        pub struct Baz;
    """)

    fun `test resolve function from nested module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::yin::yang();
                      //^ foo/baz/yin.rs
        }
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin;
    //- foo/baz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve function from nested module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::yin::yang();
                      //^ foo/baz/yin/mod.rs
        }
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin;
    //- foo/baz/yin/mod.rs
        pub fn yang() {}
    """)

    fun `test resolve module declared in included file`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
               //^ foo/baz.rs
    //- foo/baz.rs
        pub mod yin;
    //- foo/baz/yin/mod.rs
        pub fn yang() {}
    """)

    fun `test resolve module whose parent module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin;
               //^ foo/baz/yin/mod.rs
    //- foo/baz/yin/mod.rs
        pub fn yang() {}
    """)

    fun `test resolve module whose parent module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz/mod.rs
        pub mod yin;
               //^ foo/baz/yin/mod.rs
    //- foo/baz/yin/mod.rs
        pub fn yang() {}
    """)

    fun `test resolve module whose parent module declared in included file 3`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin;
               //^ foo/baz/yin.rs
    //- foo/baz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve struct from inline module whose parent module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::yin::Yang) {}
                               //^ foo/baz.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin {
            pub struct Yang;
        }
    """)

    fun `test resolve struct from inline module whose parent module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::yin::Yang) {}
                         //^ foo/baz.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod yin {
            pub struct Yang;
        }
    """)

    fun `test resolve struct from inline module whose parent module declared in included file 3`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::yin::Yang) {}
                              //^ foo/baz/mod.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz/mod.rs
        pub mod yin {
            pub struct Yang;
        }
    """)

    fun `test resolve struct from inline module whose parent module declared in included file 4`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar(b: baz::yin::Yang) {}
                         //^ foo/baz/mod.rs
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz/mod.rs
        pub mod yin {
            pub struct Yang;
        }
    """)

    fun `test resolve function from file attribute module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::yang();
                 //^ foo/yin.rs
        }
    //- foo/bar.rs
        #[path = "yin.rs"]
        pub mod baz;
    //- foo/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve function from file attribute module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::yang();
        }
    //- foo/bar.rs
        #[path = "yin.rs"]
        pub mod baz;
               //^ foo/yin.rs
    //- foo/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve function from inner file attribute module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            foobaz::baz::yang();
                        //^ foo/foobaz/yin.rs
        }
    //- foo/bar.rs
        mod foobaz {
            #[path = "yin.rs"]
            pub mod baz;
        }
    //- foo/foobaz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve function from inner file attribute module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            foobaz::bak::baz::yang();
                             //^ foo/foobaz/bak/yin.rs
        }
    //- foo/bar.rs
        mod foobaz {
            pub mod bak {
                #[path = "yin.rs"]
                pub mod baz;
            }
        }
    //- foo/foobaz/bak/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve function from inner file attribute module declared in included file 3`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::foobaz::yang();
                         //^ foo/biz/yin.rs
        }
    //- foo/bar.rs
        #[path = "biz"]
        mod baz {
            #[path = "yin.rs"]
            pub mod foobaz;
        }
    //- foo/biz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve inner file attribute module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::foobaz::yang();
                  //^ foo/biz/yin.rs
        }
    //- foo/bar.rs
        #[path = "biz"]
        mod baz {
            #[path = "yin.rs"]
            pub mod foobaz;
        }
    //- foo/biz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve inner file attribute module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::foobaz::yang();
        }
    //- foo/bar.rs
        #[path = "biz"]
        mod baz {
            #[path = "yin.rs"]
            pub mod foobaz;
                    //^ foo/biz/yin.rs
        }
    //- foo/biz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve inner file attribute module declared in included file 3`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::foobaz::yang();
        }
    //- foo/bar.rs
        mod baz {
            #[path = "yin.rs"]
            pub mod foobaz;
                    //^ foo/baz/yin.rs
        }
    //- foo/baz/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve file attribute module declared in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
        fn foobar() {
            baz::yang();
           //^ foo/yin.rs
        }
    //- foo/bar.rs
        #[path = "yin.rs"]
        pub mod baz;
    //- foo/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve file attribute module declared in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        #[path = "yin.rs"]
        pub mod baz;
               //^ foo/yin.rs
    //- foo/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve file attribute module whose parent module declared in included file`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        #[path = "yin.rs"]
        pub mod yang;
               //^ foo/yin.rs
    //- foo/yin.rs
        pub fn yang() {}
    """)

    fun `test resolve inline file attribute module whose grandparent module declared in included file`() = checkResolve("""
    //- lib.rs
        include!("foo/bar.rs");
    //- foo/bar.rs
        pub mod baz;
    //- foo/baz.rs
        pub mod foobar {
            #[path = "yin.rs"]
            pub mod yang;
                   //^ foo/baz/foobar/yin.rs
        }
    //- foo/baz/foobar/yin.rs
        pub fn yang() {}
    """)

    fun `test include in inline module 1`() = checkResolve("""
    //- lib.rs
        mod foo {
            include!("bar.rs");
        }
        fn foo(f: foo::Foo) {}
                      //^ bar.rs
    //- foo/bar.rs
        pub struct Foo;
    //- bar.rs
        pub struct Foo;
    """)

    fun `test include in inline module 2`() = checkResolve("""
    //- lib.rs
        mod foo {
            struct Foo;
            include!("bar.rs");
        }
    //- bar.rs
        fn bar(x: Foo) {}
                  //^ lib.rs
    """)

    fun `test include in function local module`() = checkResolve("""
    //- lib.rs
        fn foo() {
            mod foo {
                include!("bar.rs");
            }
            foo::Foo;
                //^ bar.rs
        }
    //- bar.rs
        struct Foo;
    """)

    fun `test include file in included file 1`() = checkResolve("""
    //- lib.rs
        include!("foo.rs");
        fn foo(x: Foo) {}
                 //^ bar.rs
    //- foo.rs
        include!("bar.rs");
    //- bar.rs
        struct Foo;
    """)

    fun `test include file in included file 2`() = checkResolve("""
    //- lib.rs
        include!("foo.rs");
        struct Foo;
    //- foo.rs
        include!("bar.rs");
    //- bar.rs
        fn foo(x: Foo) {}
                //^ lib.rs
    """)

    @ExpandMacros
    fun `test include macro in macro 1`() = checkResolve("""
    //- lib.rs
        macro_rules! generate_include {
            ($ package: tt) => {
                include!($ package);
            };
        }
        generate_include!("bar.rs");
        pub struct Foo;
    //- bar.rs
        pub fn foo(x: Foo) {}
                    //^ lib.rs
    """)

    fun `test include macro in macro 2`() = checkResolve("""
    //- lib.rs
        macro_rules! generate_include {
            ($ package: tt) => {
                include!($ package);
            };
        }
        generate_include!("bar.rs");
        pub fn foo(x: Foo) {}
                     //^ bar.rs
    //- bar.rs
        pub struct Foo;
    """)

    @ExpandMacros
    fun `test include macro in macro 3`() = checkResolve("""
    //- lib.rs
        macro_rules! generate_include {
            ($ package: tt) => {
                include!($ package);
            };
        }
        macro_rules! generate_generate_include {
            ($ mod: ident, $ package: tt) => {
                pub mod $ mod {
                    generate_include!($ package);
                }
            };
        }
        generate_generate_include!(bar, "bar.rs");
        pub fn foo(x: bar::Foo) {}
                          //^ bar.rs
    //- bar.rs
        pub struct Foo;
    """)

    @UseNewResolve
    @ExpandMacros
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test macro call in included file 1`() = checkResolve("""
    //- main.rs
        macro_rules! foo {
            () => {};
        }
        include!("foo.rs");
    //- foo.rs
        foo!();
        //^ main.rs
    """)

    @UseNewResolve
    @ExpandMacros
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test macro call in included file 2`() = checkResolve("""
    //- main.rs
        macro_rules! gen_use {
            () => { use inner::func; };
        }

        mod inner {
            pub fn func() {}
        }        //X
        include!("foo.rs");

        fn main() {
            func();
        } //^ main.rs
    //- foo.rs
        gen_use!();
    """)

    fun `test concat in include 1`() = checkResolve("""
        //- main.rs
            include!(concat!("foo.rs"));
            fn main() {
                Foo.foo();
                    //^ foo.rs
            }
        //- foo.rs
            struct Foo;
            impl Foo {
                fn foo(&self) {}
            }
    """)

    fun `test concat in include 2`() = checkResolve("""
        //- lib.rs
            include!(concat!("bar/foo.rs"));
            fn bar() {}
        //- bar/foo.rs
            fn foo() {
                bar();
            }  //^ lib.rs
    """)

    fun `test concat in include 3`() = checkResolve("""
        //- main.rs
            include!(concat!("bar", "/foo.rs"));
            fn main() {
                Foo.foo();
                    //^ bar/foo.rs
            }
        //- bar/foo.rs
            struct Foo;
            impl Foo {
                fn foo(&self) {}
            }
    """)

    fun `test recursive concat in include`() = checkResolve("""
        //- lib.rs
            include!(concat!(concat!("bar"), "/foo.rs"));
            fn bar() {}
        //- bar/foo.rs
            fn foo() {
                bar();
            }  //^ lib.rs
    """)

    fun `test fqn include`() = checkResolve("""
    //- main.rs
        std::include!("foo.rs");
        fn main() {
            println("{:?}", Foo);
                           //^ foo.rs
        }
    //- foo.rs
        #[derive(Debug)]
        struct Foo;
    """)

    fun `test fqn include and concat`() = checkResolve("""
    //- main.rs
        std::include!(std::concat!("bar", "/foo.rs"));
        fn main() {
            println("{:?}", Foo);
                           //^ bar/foo.rs
        }
    //- bar/foo.rs
        #[derive(Debug)]
        struct Foo;
    """)

    private fun checkResolve(@Language("Rust") code: String) {
        stubOnlyResolve(code) { element -> element.containingFile.virtualFile }
    }
}
