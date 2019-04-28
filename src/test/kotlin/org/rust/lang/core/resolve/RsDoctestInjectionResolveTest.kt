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

    // TODO handle existing main https://github.com/rust-lang/rust/blob/5182cc1ca/src/librustdoc/test.rs#L438
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test outer crate dependency`() = expect<IllegalStateException> {
    stubOnlyResolve("""
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
    }
}
