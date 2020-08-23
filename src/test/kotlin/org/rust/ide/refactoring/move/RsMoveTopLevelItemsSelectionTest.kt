/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.refactoring.move

import org.rust.MockEdition
import org.rust.cargo.project.workspace.CargoWorkspace

@MockEdition(CargoWorkspace.Edition.EDITION_2018)
class RsMoveTopLevelItemsSelectionTest : RsMoveTopLevelItemsTestBase() {

    fun `test cursor before item`() = doTest("""
    //- lib.rs
        /*caret*/fn foo() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo() {}
    """)

    fun `test cursor inside item`() = doTest("""
    //- lib.rs
        fn foo/*caret*/() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo() {}
    """)

    fun `test cursor after item`() = doTest("""
    //- lib.rs
        fn foo() {}/*caret*/
        fn bar() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn bar() {}
    //- main.rs
        fn foo() {}
    """)

    fun `test selected start of item`() = doTest("""
    //- lib.rs
        <selection>f</selection>n foo() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo() {}
    """)

    fun `test selected middle of item`() = doTest("""
    //- lib.rs
        fn <selection>f</selection>oo() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo() {}
    """)

    fun `test selected end of item`() = doTest("""
    //- lib.rs
        fn foo() {<selection>}</selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo() {}
    """)

    fun `test selected two items`() = doTest("""
    //- lib.rs
        fn foo1() {}
        <selection>fn foo2() {}
        fn foo3() {}</selection>
        fn foo4() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn foo1() {}

        fn foo4() {}
    //- main.rs
        fn foo2() {}

        fn foo3() {}
    """)

    fun `test selected two items at end of file`() = doTest("""
    //- lib.rs
        fn foo1() {}
        <selection>fn foo2() {}
        fn foo3() {}</selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn foo1() {}
    //- main.rs
        fn foo2() {}

        fn foo3() {}
    """)

    fun `test selected two items partially`() = doTest("""
    //- lib.rs
        fn foo1() {}
        fn foo2<selection>() {}
        fn foo3()</selection> {}
        fn foo4() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn foo1() {}

        fn foo4() {}
    //- main.rs
        fn foo2() {}

        fn foo3() {}
    """)

    fun `test selected two items with surrounding whitespace`() = doTest("""
    //- lib.rs
        <selection>
        fn foo1() {}
        fn foo2() {}
        </selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        fn foo1() {}

        fn foo2() {}
    """)

    fun `test selected two items with file attribute`() = doTest("""
    //- lib.rs
        <selection>#![attr]
        fn foo1() {}
        fn foo2() {}</selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        #![attr]
    //- main.rs
        fn foo1() {}

        fn foo2() {}
    """)

    fun `test selected two items inside inline mod 1`() = doTest("""
    //- lib.rs
        mod mod1 {
            <selection>fn foo1() {}
            fn foo2() {}</selection>
        }
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        mod mod1 {}
    //- main.rs
        fn foo1() {}

        fn foo2() {}
    """)

    fun `test selected two items inside inline mod 2`() = doTest("""
    //- lib.rs
        mod mod1 {
            fn foo1() {}
            <selection>fn foo2() {}
            fn foo3() {}</selection>
            fn foo4() {}
        }
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        mod mod1 {
            fn foo1() {}

            fn foo4() {}
        }
    //- main.rs
        fn foo2() {}

        fn foo3() {}
    """)

    fun `test selected three items`() = doTest("""
    //- lib.rs
        fn foo1() {}
        <selection>fn foo2() {}
        fn foo3() {}
        fn foo4() {}</selection>
        fn foo5() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn foo1() {}

        fn foo5() {}
    //- main.rs
        fn foo2() {}

        fn foo3() {}

        fn foo4() {}
    """)

    fun `test selected three items partially`() = doTest("""
    //- lib.rs
        fn foo1() {}
        fn foo2<selection>() {}
        fn foo3() {}
        fn foo4()</selection> {}
        fn foo5() {}
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
        fn foo1() {}

        fn foo5() {}
    //- main.rs
        fn foo2() {}

        fn foo3() {}

        fn foo4() {}
    """)

    fun `test selected empty inline mod`() = doTest("""
    //- lib.rs
        <selection>mod foo {}</selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        mod foo {}
    """)

    fun `test selected non-empty inline mod`() = doTest("""
    //- lib.rs
        <selection>mod foo { fn func() {} }</selection>
    //- main.rs
        /*target*/
    """, """
    //- lib.rs
    //- main.rs
        mod foo { fn func() {} }
    """)
}
