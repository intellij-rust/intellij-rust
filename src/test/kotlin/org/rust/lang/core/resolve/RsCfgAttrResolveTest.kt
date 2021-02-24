/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.RsTupleFieldDecl

class RsCfgAttrResolveTest : RsResolveTestBase() {
    override val followMacroExpansions: Boolean get() = true

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test fn with cfg`() = checkByCode("""
        #[cfg(intellij_rust)]
        fn foo() {}
         //X
        #[cfg(not(intellij_rust))]
        fn foo() {}

        fn main() {
            foo();
          //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test struct field with cfg`() = checkByCode("""
        struct S {
            #[cfg(not(intellij_rust))] x: u32,
            #[cfg(intellij_rust)]      x: i32,
                                     //X
        }

        fn t(f: S) {
            f.x;
            //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test tuple struct field with cfg`() = checkByCodeGeneric<RsTupleFieldDecl>("""
        struct S(#[cfg(not(intellij_rust))] u32,
                 #[cfg(intellij_rust)]      i32);
                                          //X
        fn t(f: S) {
            f.0;
            //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test tuple enum variant with cfg 1`() = checkByCode("""
        enum E {
            #[cfg(not(intellij_rust))] Variant(u32),
            #[cfg(intellij_rust)]      Variant(u32),
                                     //X
        }

        fn t() {
            E::Variant(0);
             //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test tuple enum variant with cfg 2`() = checkByCode("""
        enum E {
            #[cfg(intellij_rust)]      Variant(u32),
                                     //X
            #[cfg(not(intellij_rust))] Variant(u32),
        }

        use E::Variant;
        #[cfg(not(intellij_rust))]
        struct Variant(u32);

        fn t() {
            Variant(0);
             //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test inline mod with cfg`() = checkByCode("""
        #[cfg(intellij_rust)]
        mod my {
            pub fn bar() {}
                 //X
        }

        #[cfg(not(intellij_rust))]
        mod my {
            pub fn bar() {}
        }

        fn t() {
            my::bar();
              //^
        }
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test inline mod with cfg 2`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod my {
            fn bar() {}
             //X
            fn t() {
                bar();
            } //^
        }
     """)

    // From https://github.com/rust-lang/hashbrown/blob/09e43a8cf97f37b17768b98f28291a24c5767847/src/lib.rs#L52-L68
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import inside inline mod with cfg`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod my {
            mod inner {
                pub fn func() {}
            }
            pub use inner::*;
        }
        #[cfg(intellij_rust)]
        mod my {}

        fn t() {
            my::func();
              //^ unresolved
        }
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import overrides cfg-disabled item`() = checkByCode("""
        use foo::func;
        mod foo {
            pub fn func() {}
        }        //X

        #[cfg(not(intellij_rust))]
        fn func() {}

        mod inner {
            use super::func;
            fn main() {
                func();
            } //^
        }
     """)

    // https://github.com/clap-rs/clap/blob/5a1a209965bf28d3badafec8da6c5c975d3a686f/src/util/mod.rs#L13-L17
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test atom import of extern crate overrides cfg-disabled item`() = stubOnlyResolve("""
    //- lib.rs
        pub fn func() {}
             //X
    //- main.rs
        #[cfg(intellij_rust)]
        use test_package;

        #[cfg(not(intellij_rust))]
        mod test_package {
            pub fn func() {}
        }

        fn main() {
            test_package::func();
        } //^ lib.rs
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test resolve inside inline mod with cfg 1`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod foo {
            fn test() {
                bar();
            } //^
            fn bar() {}
             //X
        }
        #[cfg(intellij_rust)]
        mod foo {
            fn bar() {}
        }
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test resolve inside inline mod with cfg 2`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod foo {
            use crate::bar;
            fn test() {
                bar();
            } //^
        }
        fn bar() {}
         //X
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test resolve inside non-inline mod with cfg`() = stubOnlyResolve("""
    //- main.rs
        #[cfg(intellij_rust)]
        mod foo;
        #[cfg(not(intellij_rust))]
        #[path="foo_disabled.rs"]
        mod foo;
    //- foo.rs
        fn bar() {}
    //- foo_disabled.rs
        fn test() {
            bar();
        } //^ foo_disabled.rs
        fn bar() {}
         //X
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test use item with cfg`() = checkByCode("""
        mod my {
            pub fn bar() {}
                 //X
            pub fn baz() {}
        }

        #[cfg(intellij_rust)]
        use my::bar as quux;

        #[cfg(not(intellij_rust))]
        use my::baz as quux;

        fn t() {
            quux();
          //^
        }
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro expansion with cfg`() = checkByCode("""
        struct S { x: i32 }
                 //X

        macro_rules! my_macro {
            ($ t:ty) => { fn foobar() -> $ t {} };
        }

        #[cfg(intellij_rust)]
        my_macro!(S);


        #[cfg(not(intellij_rust))]
        my_macro!(i32);

        fn t() {
            foobar().x;
                   //^
        }
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test extern crate with cfg`() = stubOnlyResolve("""
    //- main.rs
        #[cfg(intellij_rust)]
        extern crate test_package;

        fn main() {
            test_package::hello();
        }               //^ lib.rs

    //- lib.rs
        pub fn hello() {}
             //X
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl`() = checkByCode("""
        struct S;
        #[cfg(intellij_rust)]
        impl S { fn foo(&self) {} }
                   //X
        #[cfg(not(intellij_rust))]
        impl S { fn foo(&self) {} }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in inline mod with cfg`() = checkByCode("""
        struct S;
        #[cfg(intellij_rust)]
        mod foo {
            impl super::S { fn foo(&self) {} }
                             //X
        }
        #[cfg(not(intellij_rust))]
        mod foo {
            impl super::S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in non-inline mod with cfg`() = stubOnlyResolve("""
    //- bar.rs
        impl super::S { pub fn foo(&self) {} }
    //- baz.rs
        impl super::S { pub fn foo(&self) {} }
    //- lib.rs
        struct S;

        #[cfg(intellij_rust)]
        #[path = "bar.rs"]
        mod foo;

        #[cfg(not(intellij_rust))]
        #[path = "baz.rs"]
        mod foo;

        fn main() {
            S.foo()
        }   //^ bar.rs
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in non-inline mod with cfg 2`() = stubOnlyResolve("""
    //- bar1.rs
        impl super::super::S { pub fn foo(&self) {} }
    //- baz1.rs
        impl super::super::S { pub fn foo(&self) {} }
    //- bar.rs
        #[path = "bar1.rs"]
        mod bar1;
    //- baz.rs
        #[path = "baz1.rs"]
        mod baz1;
    //- lib.rs
        struct S;

        #[cfg(intellij_rust)]
        #[path = "bar.rs"]
        mod foo;

        #[cfg(not(intellij_rust))]
        #[path = "baz.rs"]
        mod foo;

        fn main() {
            S.foo()
        }   //^ bar1.rs
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl in function body with cfg`() = checkByCode("""
        struct S;
        #[cfg(intellij_rust)]
        fn foo() {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(intellij_rust))]
        fn foo() {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test impl expanded from macro with cfg`() = checkByCode("""
        macro_rules! same {
            ($ i:item) => { $ i };
        }
        struct S;
        #[cfg(intellij_rust)]
        same! {
            impl S { fn foo(&self) {} }
                      //X
        }
        #[cfg(not(intellij_rust))]
        same! {
            impl S { fn foo(&self) {} }
        }
        fn main() {
            S.foo()
        }   //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg inside macros`() = checkByCode("""
        macro_rules! as_is { ($($ i:item)*) => { $($ i)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            mod spam { pub fn eggs() {} }
            #[cfg(not(intellij_rust))]
            pub use spam::*;

            #[cfg(intellij_rust)]
            mod spam {
                pub fn eggs() {}
            }        //X
            #[cfg(intellij_rust)]
            pub use spam::*;
        }
        fn main() {
            eggs();
        }  //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg on macros inside macros`() = checkByCode("""
        macro_rules! as_is { ($($ i:item)*) => { $($ i)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            as_is! {
                fn foo() {}
            }
            #[cfg(intellij_rust)]
            as_is! {
                fn foo() {}
            }    //X
        }
        fn main() {
            foo();
        }  //^
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test super mod with mod declaration with cfg`() = stubOnlyResolve("""
    //- foo.rs
        use super::bar;
                 //^ lib.rs
    //- lib.rs
        #[cfg(intellij_rust)]
        mod foo;
        fn bar() {}
    //- main.rs
        #[cfg(not(intellij_rust))]
        mod foo;
        fn bar() {}
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro definition with cfg`() = checkByCode("""
        #[cfg(intellij_rust)]
        macro_rules! foo { () -> {} }
                   //X
        #[cfg(not(intellij_rust))]
        macro_rules! foo { () -> {} }

        foo!();
        //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro in inline mod with cfg`() = checkByCode("""
        #[macro_use]
        #[cfg(intellij_rust)]
        mod foo {
            macro_rules! bar { () -> {} }
        }               //X
        #[macro_use]
        #[cfg(not(intellij_rust))]
        mod foo {
            macro_rules! bar { () -> {} }
        }
        bar!();
        //^
     """)

    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro in extracted mod with cfg`() = stubOnlyResolve("""
    //- foo.rs
        macro_rules! foo { () -> {} }
    //- bar.rs
        macro_rules! foo { () -> {} }
    //- main.rs
        #[macro_use]
        #[cfg(intellij_rust)]
        mod foo;
        #[macro_use]
        #[cfg(not(intellij_rust))]
        mod bar;
        foo!();
        //^ foo.rs
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test exported macro with cfg`() = stubOnlyResolve("""
    //- lib.rs
        mod foo;
        mod bar;
    //- foo.rs
        #[cfg(intellij_rust)]
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- bar.rs
        #[cfg(not(intellij_rust))]
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        foo!();
        //^ foo.rs
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test exported macro in mod with cfg`() = stubOnlyResolve("""
    //- lib.rs
        #[cfg(intellij_rust)]
        mod foo;
        #[cfg(not(intellij_rust))]
        mod bar;
    //- foo.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- bar.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- main.rs
        extern crate test_package;
        use test_package::foo;
        foo!();
        //^ foo.rs
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test macro from another crate with cfg`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        #[cfg(not(intellij_rust))]
        macro_rules! foo { () -> {} }
    //- main.rs
        #[macro_use]
        extern crate test_package;
        use test_package::foo;
        foo!();
        //^ unresolved
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test exported macro with cfg on "extern crate" 1`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- main.rs
        #[macro_use]
        #[cfg(intellij_rust)]
        extern crate test_package as lib;
        #[macro_use]
        #[cfg(not(intellij_rust))]
        extern crate dep_lib_target as lib;
        foo!();
        //^ lib.rs
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test exported macro with cfg on "extern crate" 2`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- main.rs
        #[cfg(intellij_rust)]
        extern crate test_package as lib;
        #[cfg(not(intellij_rust))]
        extern crate dep_lib_target as lib;
        use lib::foo;
        foo!();
        //^ lib.rs
     """)

    @ExpandMacros
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test exported macro with cfg on use item`() = stubOnlyResolve("""
    //- lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo { () -> {} }
    //- main.rs
        extern crate test_package;
        extern crate dep_lib_target;
        #[cfg(intellij_rust)]
        use test_package::foo;
        #[cfg(not(intellij_rust))]
        use dep_lib_target::foo;
        foo!();
        //^ lib.rs
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg_attr with path on mod declaration`() = stubOnlyResolve("""
    //- bar.rs
        pub fn func() {}
    //- main.rs
        #[cfg_attr(intellij_rust, path = "bar.rs")]
        mod foo;
        use foo::*;
        fn main() {
            func();
        }  //^ bar.rs
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr with path on mod declaration`() = stubOnlyResolve("""
    //- foo.rs
        pub fn func() {}
    //- main.rs
        #[cfg_attr(not(intellij_rust), path = "bar.rs")]
        mod foo;
        use foo::*;
        fn main() {
            func();
        }  //^ foo.rs
     """)

    @ExpandMacros
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-disabled macro call expanded to inline mod`() = stubOnlyResolve("""
    //- main.rs
        fn main() {
            foo::func();
        }      //^ unresolved

        macro_rules! gen_foo {
            () => {
                mod foo {
                    pub fn func() {}
                }
            };
        }

        #[cfg(not(intellij_rust))]
        gen_foo!();
     """)

    // From https://github.com/tokio-rs/tokio/blob/97c2c4203cd7c42960cac895987c43a17dff052e/tokio/src/process/mod.rs#L132-L134
    // Goal of these two tests is to check that there are no exceptions during building CrateDefMap.
    // Actual resolve result is not important, because proper multiresolve is not yet supported in new resolve.
    @ExpandMacros
    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import inside expanded shadowed mod 1`() = stubOnlyResolve("""
    //- lib.rs
        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            mod outer;
        }
    //- outer.rs
        #[cfg(intellij_rust)]
        mod my {
            pub mod inner {
                pub fn func() {}
            }        //X
            use inner::func;
            fn t() {
                func();
            } //^ outer.rs
        }
        #[cfg(not(intellij_rust))]
        mod my {}
     """)

    // From https://github.com/tokio-rs/tokio/blob/97c2c4203cd7c42960cac895987c43a17dff052e/tokio/src/process/mod.rs#L132-L134
    @ExpandMacros
    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import inside expanded shadowed mod 2`() = stubOnlyResolve("""
    //- lib.rs
        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            mod outer;
        }
    //- outer.rs
        #[cfg(not(intellij_rust))]
        mod my {}
        #[cfg(intellij_rust)]
        mod my {
            pub mod inner {
                pub fn func() {}
            }
            use inner::func;
            fn t() {
                func();
            } //^ unresolved
        }
     """)

    // From https://github.com/rust-lang/rust/blob/e0ef0fc392963438af5f0343bf7caa46fb9c3ec3/library/alloc/src/lib.rs#L164-L169
    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import to shadowed mod`() = checkByCode("""
        #[cfg(intellij_rust)]
        pub mod boxed {
            pub fn foo() {}
        }        //X
        #[cfg(not(intellij_rust))]
        mod boxed {}
        use boxed::foo;
        fn main() {
            foo();
        } //^
     """)

    // We check that there are no exceptions during building CrateDefMap (actual resolve result is not important)
    @ExpandMacros
    @MockAdditionalCfgOptions("intellij_rust")
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test import to mod shadowed by expanded mod`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod my {
            pub mod inner {
                pub fn func() {}
            }        //X
        }

        #[cfg(not(intellij_rust))]
        use inner::func;
        #[cfg(not(intellij_rust))]
        use my::inner;

        macro_rules! as_is { ($($ t:tt)*) => { $($ t)* } }
        as_is! {
            #[cfg(not(intellij_rust))]
            mod my {}
        }

        fn t() {
            func();
        } //^
     """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg under cfg_attr 1`() = checkByCode("""
        #[cfg_attr(intellij_rust, cfg(intellij_rust))]
        fn foo() {}
         //X
        #[cfg(not(intellij_rust))]
        fn foo() {}

        fn main() {
            foo();
          //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg under cfg_attr 2`() = checkByCode("""
        #[cfg_attr(not(intellij_rust), cfg(not(intellij_rust)))]
        fn foo() {}
         //X
        #[cfg(not(intellij_rust))]
        fn foo() {}

        fn main() {
            foo();
          //^
        }
    """)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg under cfg_attr 3`() = checkByCode("""
        #[cfg_attr(intellij_rust, cfg(not(intellij_rust)))]
        fn foo() {}
        fn foo() {}
         //X
        fn main() {
            foo();
          //^
        }
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test resolve with no_std attribute under enabled cfg_attr`() = stubOnlyResolve("""
    //- main.rs
        #![cfg_attr(intellij_rust, no_std)]

        fn foo(v: Vec) {}
                 //^ unresolved
    """)

    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test resolve with no_std attribute under disabled cfg_attr`() = stubOnlyResolve("""
    //- main.rs
        #![cfg_attr(not(intellij_rust), no_std)]

        fn foo(v: Vec) {}
                 //^ ...vec.rs
    """)

    fun `test unknown cfg options are disabled`() = checkByCode("""
        #[cfg(unknown_cfg_option)]
        fn foo() {}
        #[cfg(not(unknown_cfg_option))]
        fn foo() {}
         //X

        fn main() {
            foo();
          //^
        }
    """)

    fun `test unknown cargo features are disabled`() = checkByCode("""
        #[cfg(feature = "unknown_cargo_feature")]
        fn foo() {}
        #[cfg(not(feature = "unknown_cargo_feature"))]
        fn foo() {}
         //X

        fn main() {
            foo();
          //^
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    fun `test resolve to 'cfg(not(test))' in external dependencies`() = stubOnlyResolve("""
    //- dep-lib/lib.rs
        #[cfg(test)]
        #[path = "test.rs"]
        mod foo;
        #[cfg(not(test))]
        #[path = "not_test.rs"]
        mod foo;
    //- dep-lib/test.rs
        pub fn bar() {}
    //- dep-lib/not_test.rs
        pub fn bar() {}
    //- lib.rs
        fn main() {
            dep_lib_target::foo::bar();
        }                      //^ dep-lib/not_test.rs
     """)

    @UseNewResolve
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test file level cfg attribute 1`() = stubOnlyResolve("""
    //- main.rs
        #[path="foo1.rs"]
        mod foo;
        #[path="foo2.rs"]
        mod foo;
        fn main() {
            foo::func();
        }      //^ foo1.rs
    //- foo1.rs
        #![cfg(intellij_rust)]
        pub fn func() {}
    //- foo2.rs
        #![cfg(not(intellij_rust))]
        pub fn func() {}
     """)

    @UseNewResolve
    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test file level cfg attribute 2`() = stubOnlyResolve("""
    //- main.rs
        #[path="foo1.rs"]
        mod foo;
        #[path="foo2.rs"]
        mod foo;
        fn main() {
            foo::func();
        }      //^ foo2.rs
    //- foo1.rs
        #![cfg(not(intellij_rust))]
        pub fn func() {}
    //- foo2.rs
        #![cfg(intellij_rust)]
        pub fn func() {}
     """)

    @MockEdition(CargoWorkspace.Edition.EDITION_2018)
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test cfg-enabled glob import overrides cfg-disabled named import`() = checkByCode("""
        #[cfg(not(intellij_rust))]
        mod mod1 {
            pub fn func() {}
        }
        mod mod2 {
            pub fn func() {}
        }        //X

        #[cfg(not(intellij_rust))]
        use mod1::func;
        use mod2::*;

        fn main() {
            func();
        } //^
     """)
}
