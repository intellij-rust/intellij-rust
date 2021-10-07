/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.MockEdition
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor
import org.rust.cargo.project.workspace.CargoWorkspace.Edition

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

    // BACKCOMPAT: Rust 1.50. Vec struct was moved into `vec/mod.rs` since Rust 1.51
    @ProjectDescriptor(WithStdlibAndDependencyRustProjectDescriptor::class)
    fun `test resolve std element`() = stubOnlyResolve("""
    //- lib.rs
        /// ```
        /// Vec::new()
        /// //^ ...vec.rs|...vec/mod.rs
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

    @MockEdition(Edition.EDITION_2018)
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

    @MockEdition(Edition.EDITION_2018)
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

    @MockEdition(Edition.EDITION_2018)
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

    @MockEdition(Edition.EDITION_2018)
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
}
