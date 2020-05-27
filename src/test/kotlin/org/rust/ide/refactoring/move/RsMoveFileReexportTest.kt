/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.junit.Ignore
import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@Ignore
@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveFileReexportTest : RsMoveFileTestBase() {
    override val dataPath = "org/rust/ide/refactoring/move/fixtures/"

    fun `test grandparent module under reexport`() = doTest("inner1/inner2/inner3/mod1/foo.rs", "inner1/inner2/inner3/mod2")

    // parent mod of moved file contains another mod, which has function mod1_func
    // parent mod reexport that function
    // moved file can't directly access mod1_func after move
    // but can through reexport
    fun `test outside reference to reexported item`() = doTest(
        "mod1/foo.rs",
        "mod2",
        """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod mod1_inner {  // private
            pub fn mod1_func() {}
        }
        pub use mod1_inner::mod1_func;

        mod foo;
    //- mod2/mod.rs
    //- mod1/foo.rs
        fn func() {
            super::mod1_func();
        }
    """, """
    //- main.rs
        mod mod1;
        mod mod2;
    //- mod1/mod.rs
        mod mod1_inner {  // private
            pub fn mod1_func() {}
        }
        pub use mod1_inner::mod1_func;
    //- mod2/mod.rs
        mod foo;
    //- mod2/foo.rs
        fn func() {
            crate::mod1::mod1_func();
        }
    """)

    // moved file has function `func`,
    // which can't be directly accessed from 'usages' mod
    // but can through reexports
    fun `test moved file under reexport before move`() = doTest(
        "inner/mod1/foo.rs",
        "inner/mod2",
        """
    //- main.rs
        mod inner {
            mod mod1;
            mod mod2;
            pub use mod1::*;
        }
        mod usages {
            fn test() {
                crate::inner::foo::func();
            }
        }
    //- inner/mod1/mod.rs
        pub mod foo;
    //- inner/mod2/mod.rs
    //- inner/mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod inner {
            mod mod1;
            mod mod2;
            pub use mod1::*;
            pub use mod2::foo;
        }
        mod usages {
            fn test() {
                crate::inner::foo::func();
            }
        }
    //- inner/mod1/mod.rs
    //- inner/mod2/mod.rs
        pub mod foo;
    //- inner/mod2/foo.rs
        pub fn func() {}
    """)

    fun `test moved file under reexport after move`() = doTest(
        "inner/mod1/foo.rs",
        "inner/mod2",
        """
    //- main.rs
        mod inner {
            pub mod mod1;
            mod mod2;  // private
            pub use mod2::*;
        }
        mod usages {
            fn test() {
                crate::inner::mod1::foo::func();
            }
        }
    //- inner/mod1/mod.rs
        pub mod foo;
    //- inner/mod2/mod.rs
    //- inner/mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod inner {
            pub mod mod1;
            mod mod2;  // private
            pub use mod2::*;
        }
        mod usages {
            fn test() {
                crate::inner::foo::func();
            }
        }
    //- inner/mod1/mod.rs
    //- inner/mod2/mod.rs
        pub mod foo;
    //- inner/mod2/foo.rs
        pub fn func() {}
    """)

    fun `test both parent modules under reexport`() = doTest(
        "inner1/inner2/mod1/foo.rs",
        "inner1/inner2/mod2",
        """
    //- main.rs
        mod inner1 {
            mod inner2 {
                pub mod mod1;
                pub mod mod2;
            }
            pub use inner2::*;
        }
        mod usages {
            fn test() {
                crate::inner1::mod1::foo::func();
            }
        }
    //- inner1/inner2/mod1/mod.rs
        pub mod foo;
    //- inner1/inner2/mod2/mod.rs
    //- inner1/inner2/mod1/foo.rs
        pub fn func() {}
    """, """
    //- main.rs
        mod inner1 {
            mod inner2 {
                pub mod mod1;
                pub mod mod2;
            }
            pub use inner2::*;
        }
        mod usages {
            fn test() {
                crate::inner1::mod2::foo::func();
            }
        }
    //- inner1/inner2/mod1/mod.rs
    //- inner1/inner2/mod2/mod.rs
        pub mod foo;
    //- inner1/inner2/mod2/foo.rs
        pub fn func() {}
    """)
}
