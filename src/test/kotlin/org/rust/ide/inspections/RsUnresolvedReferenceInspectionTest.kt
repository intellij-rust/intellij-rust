/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections

import org.intellij.lang.annotations.Language

import org.rust.*
import org.rust.cargo.project.workspace.CargoWorkspace.Edition
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS

class RsUnresolvedReferenceInspectionTest : RsInspectionsTestBase(RsUnresolvedReferenceInspection::class) {

    fun `test unresolved reference with quick fix`() = checkByText("""
        mod foo {
            pub struct Foo;
        }

        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo</error>;
        }
    """)

    fun `test unresolved references without quick fix 1`() = checkByText("""
        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo</error>;
        }
    """, true)

    fun `test unresolved references without quick fix 2`() = checkByText("""
        fn main() {
            let x = <error descr="Unresolved reference: `Foo`">Foo</error>;
        }
    """, false)

    fun `test unresolved references without quick fix 3`() = checkByText("""
        struct Foo;
        impl Foo {}
        fn main() {
            let x = Foo::bar;
        }
    """, true)

    fun `test reference with multiple resolve`() = checkByText("""
        #[cfg(unix)]
        fn foo() {}
        #[cfg(windows)]
        fn foo() {}

        fn main() {
            foo();
        }
    """, false)

    fun `test do not highlight generic params`() = checkByText("""
        mod foo_bar {
            pub struct Foo<T>(T);
            pub struct Bar<T>(T);
        }

        fn foo<T>() -> <error descr="Unresolved reference: `Foo`">Foo</error><<error descr="Unresolved reference: `Bar`">Bar</error><T>> {

        }
    """)

    fun `test unresolved method reference`() = checkByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo</error>();
        }
    """)

    fun `test unresolved method references without quick fix 1`() = checkByText("""
        fn main() {
            let x = 123.foo();
        }
    """, true)

    fun `test unresolved method references without quick fix 2`() = checkByText("""
        fn main() {
            let x = 123.<error descr="Unresolved reference: `foo`">foo</error>();
        }
    """, false)

    fun `test do not highlight method reference with local import`() = checkByText("""
        mod foo {
            pub trait Foo {
                fn foo(&self);
            }

            impl<T> Foo for T {
                fn foo(&self) {}
            }
        }

        fn main() {
            use foo::Foo;
            123.foo();
        }
    """)

    // TODO should actually be E0423
    fun `test wrong namespace`() = checkByText("""
        use foo::Foo;

        mod foo {
            pub struct Foo {}
        }

        fn main() {
            /*error descr="Unresolved reference: `Foo`"*/Foo/*error**/
        }
    """)

    fun `test do not highlight unresolved method of trait bound if multiple defs (invalid code)`() = checkByText("""
        mod foo {
            pub trait Trait {
                fn foo(&self) {}
                fn foo(&self) {}
            }
        }
        fn bar<T: foo::Trait>(t: T) {
            t.foo(&t); // no error here
        }
    """)

    fun `test no unresolved reference for 'Self' type`() = checkByText("""
        struct Foo;
        impl Foo { fn foo() -> Self {} }
    """, false)

    fun `test unresolved reference for module`() = checkByText("""
        use <error descr="Unresolved reference: `foo`">foo</error>::bar::Foo;
    """, false)

    fun `test unresolved reference for nested module`() = checkByText("""
        mod foo { }
        use foo::<error descr="Unresolved reference: `bar`">bar</error>::Foo;
    """, false)

    fun `test unresolved reference for struct in nested module 1`() = checkByText("""
        mod foo {
            pub mod bar { }
        }
        use foo::bar::<error descr="Unresolved reference: `Foo`">Foo</error>;
    """, false)

    fun `test unresolved reference for struct in nested module 2`() = checkByText("""
        mod foo { }
        use foo::<error descr="Unresolved reference: `bar`">bar</error>::{qux, boo};
    """, false)

    fun `test unresolved reference for function in nested module`() = checkByText("""
        mod foo { }
        fn main() {
            foo::<error descr="Unresolved reference: `bar`">bar</error>::Qux::foobar();
        }
    """, false)

    fun `test no unresolved reference for UFCS with trait`() = checkByText("""
        mod foo {
            pub trait Bar {
                fn baz(&self) {}
            }
        }
        struct S;
        use foo::Bar;
        impl Bar for S {}
        fn main() {
            Bar::baz(&S)
        }
    """)

    fun `test no unresolved reference for UFCS with qualified trait`() = checkByText("""
        mod foo {
            pub trait Bar {
                fn baz(&self) {}
            }
        }
        struct S;
        impl foo::Bar for S {}
        fn main() {
            foo::Bar::baz(&S)
        }
    """)

    fun `test no unresolved reference for a path after incomplete path 1`() = checkByText("""
        use <error descr="Unresolved reference: `foo`">foo</error>::<error descr="identifier expected, got '::'">:</error>:bar;
    """, false)

    fun `test no unresolved reference for a path after incomplete path 2`() = checkByText("""
        mod foo {}
        use foo::<error descr="identifier expected, got '::'">:</error>:bar;
    """, false)

    @SkipTestWrapping // TODO remove after enabling expansion for extern crates
    @MockAdditionalCfgOptions("intellij_rust")
    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test unknown crate E0463`() = checkByText("""
        extern crate alloc;

        extern crate <error descr="Can't find crate for `litarvan` [E0463]">litarvan</error>;

        #[cfg(intellij_rust)]
        extern crate <error descr="Can't find crate for `unknown_crate1` [E0463]">unknown_crate1</error>;
        #[cfg(not(intellij_rust))]
        extern crate unknown_crate2;
    """)

    @MinRustcVersion("1.56.0-nightly")
    @MockEdition(Edition.EDITION_2021)
    @ProjectDescriptor(WithStdlibRustProjectDescriptor::class)
    fun `test 2021 edition prelude`() = checkByText("""
        fn main() {
            char::try_from(0u32);
        }
    """, false)

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    fun `test no unresolved reference for built-in attributes`() = checkByText("""
        #[!forbid()]

        #[allow(foo)]
        #[rustfmt::skip]
        #[clippy::foo]
        #[doc = "docs"]
        #[cfg_attr(all(unix, unix), allow(foo::bar))]
        fn foo() { }

        #[derive(Clone, Copy)]
        struct S;
    """, false)

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test attribute macro`() = checkByFileTree("""
    //- dep-proc-macro/lib.rs
        use proc_macro::TokenStream;

        #[proc_macro_attribute]
        pub fn foobar(attr: TokenStream, item: TokenStream) -> TokenStream {
            item
        }
    //- main.rs
        use dep_proc_macro::foobar;/*caret*/

        #[dep_proc_macro::foobar]
        fn foo() { }

        #[foobar]
        fn bar() { }

        #[dep_proc_macro::<error descr="Unresolved reference: `unresolved`">unresolved</error>]
        fn baz() { }

        #[<error descr="Unresolved reference: `unresolved`">unresolved</error>]
        fn qux() { }
    """, false)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test no errors in cfg-disabled file`() = checkByFileTree("""
    //- main.rs
        #[cfg(not(intellij_rust))]
        mod foo;
        #[cfg(not(intellij_rust))]
        struct MyStruct;
    //- foo.rs
        use crate::MyStruct;
        macro_rules! foo {() => {};}
        foo!();/*caret*/
        fn foo(a: MyStruct) {}
    """, false)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test there are errors in a file that both cfg-enabled and cfg-disabled`() = checkByFileTree("""
    //- main.rs
        #[cfg(not(intellij_rust))]
        mod foo;
        #[cfg(intellij_rust)]
        mod foo;
    //- foo.rs
        use crate::<error descr="Unresolved reference: `MyStruct`">MyStruct</error>;
        fn foo(a: <error descr="Unresolved reference: `MyStruct`">MyStruct</error>) {}/*caret*/
    """, false)

    fun `test no unresolved reference if path qualifier is multiresolved`() = checkByText("""
        mod foo {
            fn foo() {}
        }
        mod foo {
            fn bar() {}
        }
        fn main () {
            foo::bar();
        }
    """, false)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no errors in cfg-test mod when there are cyclic dev-dependencies in the package`() = checkByFileTree("""
    //- cyclic-dep-lib-dev-dep/lib.rs
        pub fn foo() {}
    //- dep-lib-with-cyclic-dep/lib.rs
        #[cfg(test)]
        mod tests {
            use cyclic_dep_lib_dev_dep::foo;/*caret*/
            use cyclic_dep_lib_dev_dep::bar;
        }
    """, false)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test no errors in test fn when there are cyclic dev-dependencies in the package`() = checkByFileTree("""
    //- cyclic-dep-lib-dev-dep/lib.rs
        pub fn foo() {}
    //- dep-lib-with-cyclic-dep/lib.rs
        #[test]
        fn test() {
            use cyclic_dep_lib_dev_dep::foo;/*caret*/
            use cyclic_dep_lib_dev_dep::bar;
        }
    """, false)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test there are in cfg-test mod when there aren't cyclic dev-dependencies in the package`() = checkByFileTree("""
    //- trans-lib/lib.rs
        pub fn foo() {}
    //- dep-lib/lib.rs
        #[cfg(test)]
        mod tests {
            use trans_lib::foo;/*caret*/
            use trans_lib::<error descr="Unresolved reference: `bar`">bar</error>;
        }
    """, false)

    // https://github.com/intellij-rust/intellij-rust/issues/8962
    fun `test no unresolved reference for explicit type-qualified associated member path`() = checkByText("""
        struct S;
        mod module {
            pub trait Trait { fn convert(self); }
            impl Trait for super::S { fn convert(self) {} }
        }
        fn main() {
            <S as module::Trait>::convert(S);
        }
    """)

    private fun checkByText(@Language("Rust") text: String, ignoreWithoutQuickFix: Boolean) {
        withIgnoreWithoutQuickFix(ignoreWithoutQuickFix) { checkByText(text) }
    }

    private fun checkByFileTree(@Language("Rust") text: String, ignoreWithoutQuickFix: Boolean) {
        withIgnoreWithoutQuickFix(ignoreWithoutQuickFix) { checkByFileTree(text) }
    }

    private fun withIgnoreWithoutQuickFix(ignoreWithoutQuickFix: Boolean, check: () -> Unit) {
        val inspection = inspection as RsUnresolvedReferenceInspection
        val defaultValue = inspection.ignoreWithoutQuickFix
        try {
            inspection.ignoreWithoutQuickFix = ignoreWithoutQuickFix
            check()
        } finally {
            inspection.ignoreWithoutQuickFix = defaultValue
        }
    }
}
