/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.*
import org.rust.ide.experiments.RsExperiments
import org.rust.lang.core.macros.MacroExpansionScope

class RsDoctestInjectionResolveTest : RsResolveTestBase() {
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test resolve outer element`() = checkByCode("""
        /// ```
        /// use test_package::foo;
        /// foo();
        /// //^
        /// ```
        pub fn foo() {}
         //X
    """, "lib.rs")

    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test resolve std element`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// Vec::new()
        /// //^ ...vec/mod.rs
        /// ```
        pub fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test outer crate dependency`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// extern crate dep_lib_target;
        /// fn main() {
        ///     use dep_lib_target::bar;
        ///                       //^ dep-lib/lib.rs
        /// }
        /// ```
        pub fn foo() {}
    //- dep-lib/lib.rs
        pub fn bar() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test macro`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// #[macro_use]
        /// extern crate test_package;
        /// fn main() {
        ///     foo!();
        ///   //^ lib.rs
        /// }
        /// ```
        #[macro_export]
        macro_rules! foo {
            () => {};
        }
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test macro 2`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// use test_package::foo;
        /// fn main() {
        ///     foo!();
        ///   //^ lib.rs
        /// }
        /// ```
        pub macro foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test extra extern crate`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// extern crate test_package;
        /// fn main() {
        ///     use test_package::foo;
        ///     foo();
        ///   //^ lib.rs
        /// }
        /// ```
        pub fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test extra extern crate without main function`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// extern crate test_package;
        /// use test_package::foo;
        /// foo();
        /// //^ lib.rs
        /// ```
        pub fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test qualified macro call inside function`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// fn test() {
        ///     dep_lib_target::bar!();
        /// }                 //^ dep-lib/lib.rs
        /// ```
        pub fn foo() {}
    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! bar { () => {}; }
                   //X
    """)

    fun `test resolve to inline mod`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// mod inner {
        ///     pub fn func() {}
        /// }
        /// use inner::func;
        /// fn main() {
        ///     func();
        /// } //^ ...lib.rs
        /// ```
        fn foo() {}
    """)

    fun `test resolve in inline mod`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// mod inner {
        ///     fn func() {}
        ///     fn main() {
        ///         func();
        ///     } //^ ...lib.rs
        /// }
        /// ```
        fn foo() {}
    """)

    fun `test resolve to super mod`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// fn func() {}
        /// mod inner {
        ///     fn main() {
        ///         super::func();
        ///     }        //^ ...lib.rs
        /// }
        /// ```
        fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test resolve to transitive dependency`() = stubOnlyResolve("""
    //- trans-lib/lib.rs
        pub fn func() {}
    //- dep-lib/lib.rs
        pub use trans_lib::func;
    //- lib.rs
        /// ```
        /// use dep_lib_target::func;
        /// fn main() {
        ///     func();
        /// } //^ ...trans-lib/lib.rs
        /// ```
        fn foo() {}
    """)

    @MinRustcVersion("1.46.0")
    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @WithExperimentalFeatures(RsExperiments.EVALUATE_BUILD_SCRIPTS, RsExperiments.PROC_MACROS)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test attribute proc macros`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// use test_proc_macros::attr_replace_with_attr;
        ///
        /// #[attr_replace_with_attr(struct X{})]
        /// foo! {}                       //X
        /// fn main() {
        ///     let _: X;
        /// }        //^ ...lib.rs
        /// ```
        fn foo() {}
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test impl`() = checkByCode("""
        /// ```
        /// use test_package::Bar;
        /// struct Foo;
        /// impl Foo {
        ///      fn foo(&self) -> Bar {}
        /// }
        /// Foo.foo().bar();
        ///         //^
        /// ```
        pub struct Bar;
        impl Bar {
            pub fn bar(&self) {}
        }        //X
    """, "lib.rs")

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test impl outside of main`() = checkByCode("""
        /// ```
        /// use test_package::Bar;
        /// struct Foo;
        /// impl Foo {
        ///      fn foo(&self) -> Bar {}
        /// }
        /// fn main() {
        ///     Foo.foo().bar();
        ///             //^
        /// }
        /// ```
        pub struct Bar;
        impl Bar {
            pub fn bar(&self) {}
        }        //X
    """, "lib.rs")
}
