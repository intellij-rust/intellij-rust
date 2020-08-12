/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveTopLevelItemsTest : RsMoveTopLevelItemsTestBase() {

    fun `test simple`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
        mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {}
        mod mod2 {
            fn foo() {}
        }
    """)

    fun `test absolute outside reference which should be changed because of reexports`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use bar::*;
            mod mod1 {
                fn foo/*caret*/() { crate::inner1::bar::bar_func(); }
            }
            // private
            mod bar { pub fn bar_func() {} }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod inner1 {
            pub use bar::*;
            mod mod1 {}
            // private
            mod bar { pub fn bar_func() {} }
        }
        mod mod2 {
            fn foo() { crate::inner1::bar_func(); }
        }
    """)

    fun `test outside reference to reexported item`() = doTest("""
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::bar;
            mod mod1_inner {
                pub fn bar() {}
            }
            fn foo/*caret*/() { mod1_inner::bar(); }
        }
        mod mod2/*target*/ {}
    """, """
    //- lib.rs
        mod mod1 {
            pub use mod1_inner::bar;
            mod mod1_inner {
                pub fn bar() {}
            }
        }
        mod mod2 {
            use crate::mod1;

            fn foo() { mod1::bar(); }
        }
    """)

    fun `test inside reference, moved item under reexport before move`() = doTest("""
    //- lib.rs
        mod inner {
            pub use mod1::*;
            // private
            mod mod1 {
                pub fn foo/*caret*/() {}
            }
            pub mod mod2/*target*/ {}
        }
        mod usage {
            fn test() { crate::inner::foo(); }
        }
    """, """
    //- lib.rs
        mod inner {
            pub use mod1::*;
            // private
            mod mod1 {}
            pub mod mod2 {
                pub fn foo() {}
            }
        }
        mod usage {
            fn test() { crate::inner::mod2::foo(); }
        }
    """)

    fun `test inside reference, moved item under reexport after move`() = doTest("""
    //- lib.rs
        mod inner {
            pub use mod2::*;
            pub mod mod1 {
                pub fn foo/*caret*/() {}
            }
            // private
            mod mod2/*target*/ {
                pub fn bar() {}
            }
        }
        mod usage {
            fn test() { crate::inner::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner {
            pub use mod2::*;
            pub mod mod1 {}
            // private
            mod mod2 {
                pub fn bar() {}

                pub fn foo() {}
            }
        }
        mod usage {
            fn test() { crate::inner::foo(); }
        }
    """)

    fun `test inside reference, both source and target parent modules under reexport`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {
                pub mod mod1 {
                    pub fn foo/*caret*/() {}
                }
                pub mod mod2/*target*/ {}
            }
        }
        mod usage {
            fn test() { crate::inner1::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {
                pub mod mod1 {}
                pub mod mod2 {
                    pub fn foo() {}
                }
            }
        }
        mod usage {
            fn test() { crate::inner1::mod2::foo(); }
        }
    """)

    fun `test inside reference, grandparent module under reexport`() = doTest("""
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {  // private
                pub mod mod1 {
                    pub fn foo/*caret*/() {}
                }
                pub mod mod2/*target*/ {}
            }
        }

        fn test1() { inner1::mod1::foo(); }
        mod usages {
            fn test2() { crate::inner1::mod1::foo(); }
        }
    """, """
    //- lib.rs
        mod inner1 {
            pub use inner2::*;
            mod inner2 {  // private
                pub mod mod1 {}
                pub mod mod2 {
                    pub fn foo() {}
                }
            }
        }

        fn test1() { inner1::mod2::foo(); }
        mod usages {
            fn test2() { crate::inner1::mod2::foo(); }
        }
    """)

    fun `test move to other crate simple`() = doTest("""
    //- main.rs
        mod mod1 {
            fn foo/*caret*/() {}
        }
    //- lib.rs
        mod mod2/*target*/ {}
    """, """
    //- main.rs
        mod mod1 {}
    //- lib.rs
        mod mod2 {
            fn foo() {}
        }
    """)
}
