/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor

@ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
class RsPackageLibraryResolveTest : RsResolveTestBase() {
    fun `test library as crate`() = stubOnlyResolve("""
    //- main.rs
        extern crate test_package;

        fn main() {
            test_package::hello();
        }         //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)

    fun `test crate alias`() = stubOnlyResolve("""
    //- main.rs
        extern crate test_package as other_name;

        fn main() {
            other_name::hello();
        }                //^ lib.rs

    //- lib.rs
        pub fn hello() {}
    """)


    fun `test macro rules`() = stubOnlyResolve("""
    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo_bar!();
        }  //^ lib.rs
    //- lib.rs
        #[macro_export]
        macro_rules! foo_bar { () => {} }
    """)

    fun `test macro rules missing macro_export`() = stubOnlyResolve("""
    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo_bar!();
        }  //^ unresolved
    //- lib.rs
        // Missing #[macro_export] here
        macro_rules! foo_bar { () => {} }
    """, NameResolutionTestmarks.missingMacroExport)

    fun `test macro rules missing macro_use`() = stubOnlyResolve("""
    //- main.rs
        // Missing #[macro_use] here
        extern crate test_package;

        fn main() {
            foo_bar!();
        }  //^ unresolved
    //- lib.rs
        #[macro_export]
        macro_rules! foo_bar { () => {} }
    """, NameResolutionTestmarks.missingMacroUse)

    fun `test macro rules in mod 1`() = stubOnlyResolve("""
    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo_bar!();
        }  //^ lib.rs
    //- lib.rs
        mod foo {
            #[macro_export]
            macro_rules! foo_bar { () => {} }
        }
    """)

    fun `test macro rules in mod 2`() = stubOnlyResolve("""
    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo_bar!();
        }  //^ foo.rs
    //- lib.rs
        mod foo;
    //- foo.rs
        #[macro_export]
        macro_rules! foo_bar { () => {} }
    """)

    fun `test macro reexport in use item`() = stubOnlyResolve("""
    //- lib.rs
        #![feature(use_extern_macros)]

        #[macro_use]
        extern crate dep_lib_target;

        pub use dep_lib_target::foo;
                               //^ dep-lib/lib.rs
    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
            () => {};
        }
    """)

    fun `test new macro reexport`() = stubOnlyResolve("""
    //- lib.rs
        #![feature(use_extern_macros)]

        extern crate dep_lib_target;

        pub use dep_lib_target::foo;

    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
            () => {};
        }

    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo!();
            //^ dep-lib/lib.rs
        }
    """)

    fun `test new macro reexport with crate alias`() = stubOnlyResolve("""
    //- lib.rs
        #![feature(use_extern_macros)]

        extern crate dep_lib_target as dep_lib;

        pub use dep_lib::foo;

    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
            () => {};
        }

    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo!();
            //^ dep-lib/lib.rs
        }
    """)

    fun `test new macro reexport from inner module`() = stubOnlyResolve("""
    //- lib.rs
        #![feature(use_extern_macros)]

        extern crate dep_lib_target;

        pub use dep_lib_target::foo;

    //- dep-lib/lib.rs
        mod macros;

    //- dep-lib/macros.rs
        #[macro_export]
        macro_rules! foo {
            () => {};
        }

    //- main.rs
        #[macro_use]
        extern crate test_package;

        fn main() {
            foo!();
            //^ dep-lib/macros.rs
        }
    """)

    fun `test reexported macros are visible in reexporting mod`() = stubOnlyResolve("""
    //- lib.rs
        #![feature(use_extern_macros)]

        extern crate dep_lib_target;

        pub use dep_lib_target::foo;

        fn bar() {
            foo!();
            //^ dep-lib/lib.rs
        }

    //- dep-lib/lib.rs
        #[macro_export]
        macro_rules! foo {
            () => {};
        }
    """, NameResolutionTestmarks.missingMacroUse)

    fun `test import from crate root without 'pub' vis`() = stubOnlyResolve("""
    //- lib.rs
        mod foo {
            pub mod bar {
                pub struct S;
            }
        }
        use foo::bar;

        mod baz;
    //- baz.rs
        use bar::S;
               //^ lib.rs
    """)
}
