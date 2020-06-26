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
