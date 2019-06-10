/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.inspections.naming

import org.rust.ide.inspections.RsInspectionsTestBase
import org.rust.ide.inspections.RsMethodNamingInspection

class RsMethodNamingInspectionTest : RsInspectionsTestBase(RsMethodNamingInspection::class) {
    fun `test methods`() = checkByText("""
        struct Foo {}
        impl Foo {
            fn met_ok(&self) {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>(&self) {}
        }
    """)

    fun `test methods suppression`() = checkByText("""
        #![allow(non_snake_case)]
        struct Foo {}
        impl Foo {
            fn MET_BAR(&self) {}
        }
    """)

    fun `test methods fix`() = checkFixByText("Rename to `met_bar`", """
        struct Foo;
        impl Foo {
            fn <warning descr="Method `MetBar` should have a snake case name such as `met_bar`">MetB<caret>ar</warning>(&self) {}
        }
        fn method_use() {
            let f = Foo {};
            f.MetBar();
        }
    """, """
        struct Foo;
        impl Foo {
            fn met_bar(&self) {}
        }
        fn method_use() {
            let f = Foo {};
            f.met_bar();
        }
    """)

    fun `test trait methods`() = checkByText("""
        trait Foo {
            fn met_ok() {}
            fn <warning descr="Method `MET_BAR` should have a snake case name such as `met_bar`">MET_BAR</warning>() {}
        }
    """)

    fun `test trait methods suppression`() = checkByText("""
        trait Foo {
            #[allow(non_snake_case)]
            fn MET_BAR() {}
        }
    """)

    // TODO: Uncomment when trait methods support renaming
    //
    // fun `test trait methods fix`() = checkFixByText("Rename to `bar_baz`", """
    //     trait Foo {
    //         fn <warning descr="Method `BarBaz` should have a snake case name such as `bar_baz`">Bar<caret>Baz</warning>() {}
    //     }
    //     struct Food;
    //     impl Foo for Food {
    //         fn BarBaz() {}
    //     }
    // """, """
    //     trait Foo {
    //         fn bar_baz() {}
    //     }
    //     struct Food;
    //     impl Foo for Food {
    //         fn bar_baz() {}
    //     }
    // """)
}
