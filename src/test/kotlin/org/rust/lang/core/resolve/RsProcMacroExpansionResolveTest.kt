/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition.EDITION_2018
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope

@MinRustcVersion("1.46.0")
@MockEdition(EDITION_2018)
@ExpandMacros(MacroExpansionScope.WORKSPACE)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
class RsProcMacroExpansionResolveTest : RsResolveTestBase() {
    fun `test simple function-like macro`() = checkByCode("""
        use test_proc_macros::function_like_as_is;

        struct Foo;
        impl Foo {
            fn bar(&self) {}
        }     //X

        function_like_as_is! {
            fn foo() -> Foo { Foo }
        }

        fn main() {
            foo().bar()
        }       //^
    """)

    @UseNewResolve
    fun `test custom derive`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[derive(DeriveImplForFoo)] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    fun `test custom derive dollar crate`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        macro_rules! foo {
            () => {
                #[derive($ crate::DeriveImplForFoo)]
                struct Foo;
            };
        }
        foo!();
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }     //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in enabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(intellij_rust, derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }    //X

        fn main() {
            Foo.foo().bar()
        }           //^
    """)

    @UseNewResolve
    @MockAdditionalCfgOptions("intellij_rust")
    fun `test custom derive in disabled cfg_attr attribute`() = checkByCode("""
        use test_proc_macros::DeriveImplForFoo;

        #[cfg_attr(not(intellij_rust), derive(DeriveImplForFoo))] // impl Foo { fn foo(&self) -> Bar {} }
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)

    @UseNewResolve
    fun `test not expanded if not a custom derive macro is used in custom derive position`() = checkByCode("""
        use test_proc_macros::function_like_generates_impl_for_foo;

        #[derive(function_like_generates_impl_for_foo)] // Not a custom derive
        struct Foo;
        struct Bar;
        impl Bar {
            fn bar(&self) {}
        }

        fn main() {
            Foo.foo().bar()
        }           //^ unresolved
    """)
}
