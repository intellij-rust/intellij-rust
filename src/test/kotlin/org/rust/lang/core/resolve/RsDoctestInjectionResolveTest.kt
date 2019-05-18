/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.WithStdlibAndDependencyRustProjectDescriptor

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
        /// //^ ...vec.rs
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
}
