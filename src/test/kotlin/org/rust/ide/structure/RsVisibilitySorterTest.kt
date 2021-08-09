/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.structure

import com.intellij.ide.util.treeView.smartTree.Sorter
import org.intellij.lang.annotations.Language

class RsVisibilitySorterTest : RsStructureViewToggleableActionTest(){
    override val actionId: String = RsVisibilitySorter.ID

    fun `test visibility sort on fields`() = doTest("""
        mod foo {
            mod too {
                mod bar {}
                struct Struct {
                    my_hidden: bool,
                    pub(super) my_super: bool,
                    pub(self) my_self: bool,
                    pub(crate) my_crate: bool,
                    pub(in self::bar) my_invalid: bool,
                    pub(in super::super) my_double_sup: bool,
                    pub(in crate::foo) my_path: bool,
                    pub my_pub: bool,
                    pub my_rec: &'static Struct,
                }
            }
        }
    """, """
        |-main.rs visibility=none
        | -foo visibility=private
        |  -too visibility=private
        |   bar visibility=private
        |   -Struct visibility=private
        |    my_hidden: bool visibility=private
        |    my_super: bool visibility=restricted
        |    my_self: bool visibility=restricted
        |    my_crate: bool visibility=restricted
        |    my_invalid: bool visibility=restricted
        |    my_double_sup: bool visibility=restricted
        |    my_path: bool visibility=restricted
        |    my_pub: bool visibility=public
        |    my_rec: &'static Struct visibility=public
    """, """
        |-main.rs visibility=none
        | -foo visibility=private
        |  -too visibility=private
        |   bar visibility=private
        |   -Struct visibility=private
        |    my_pub: bool visibility=public
        |    my_rec: &'static Struct visibility=public
        |    my_super: bool visibility=restricted
        |    my_self: bool visibility=restricted
        |    my_crate: bool visibility=restricted
        |    my_invalid: bool visibility=restricted
        |    my_double_sup: bool visibility=restricted
        |    my_path: bool visibility=restricted
        |    my_hidden: bool visibility=private
    """)

    fun `test visibility sort with hidden structs`() = test("""
        struct HiddenStruct {
            invisible: bool,
            pub(super) semi_visible: bool,
            pub(crate) restricted: bool,
            pub visible: bool,
        }

        struct Hidden;

        struct HiddenTuple(usize, pub u32, pub(crate) bool);
    """, """
        |-HiddenStruct visibility=private
        | invisible: bool visibility=private
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | visible: bool visibility=public
        |Hidden visibility=private
        |HiddenTuple visibility=private
    """, """
        |-HiddenStruct visibility=private
        | visible: bool visibility=public
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | invisible: bool visibility=private
        |Hidden visibility=private
        |HiddenTuple visibility=private
    """)

    fun `test visibility sort with restricted structs`() = test("""
        pub(super) struct RestrictedStruct {
            invisible: bool,
            pub(super) semi_visible: bool,
            pub(crate) restricted: bool,
            pub visible: bool,
        }

        pub(super) struct Restricted;

        pub(crate) struct RestrictedTuple(usize, pub u32, pub(crate) bool);
    """, """
        |-RestrictedStruct visibility=restricted
        | invisible: bool visibility=private
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | visible: bool visibility=public
        |Restricted visibility=restricted
        |RestrictedTuple visibility=restricted
    """, """
        |-RestrictedStruct visibility=restricted
        | visible: bool visibility=public
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | invisible: bool visibility=private
        |Restricted visibility=restricted
        |RestrictedTuple visibility=restricted
    """)

    fun `test visibility sort with public structs`() = test("""
        pub struct VisibleStruct {
            invisible: bool,
            pub(super) semi_visible: bool,
            pub(crate) restricted: bool,
            pub visible: bool,
        }

        pub struct Visible;

        pub struct VisibleTuple(usize, pub u32, pub(crate) bool);
    """, """
        |-VisibleStruct visibility=public
        | invisible: bool visibility=private
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | visible: bool visibility=public
        |Visible visibility=public
        |VisibleTuple visibility=public
    """, """
        |-VisibleStruct visibility=public
        | visible: bool visibility=public
        | semi_visible: bool visibility=restricted
        | restricted: bool visibility=restricted
        | invisible: bool visibility=private
        |Visible visibility=public
        |VisibleTuple visibility=public
    """)

    fun `test visibility sort with enums`() = test("""
        struct Hidden;

        enum EnumHidden {
            First,
            Second { foo: Hidden, bar: usize },
            Third(Hidden),
        }

        pub(super) enum EnumRestricted {
            First,
            Second { foo: Hidden, bar: usize },
            Third(Hidden),
        }

        pub enum EnumVisible {
            First,
            Second { foo: Hidden, bar: usize },
            Third(Hidden),
        }
    """, """
        |Hidden visibility=private
        |-EnumHidden visibility=private
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
        |-EnumRestricted visibility=restricted
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
        |-EnumVisible visibility=public
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
    """, """
        |-EnumVisible visibility=public
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
        |-EnumRestricted visibility=restricted
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
        |Hidden visibility=private
        |-EnumHidden visibility=private
        | First visibility=none
        | -Second visibility=none
        |  foo: Hidden visibility=none
        |  bar: usize visibility=none
        | Third(Hidden) visibility=none
    """)

    fun `test visibility sort with functions`() = test("""
        struct Hidden;

        fn private_fn(x: Hidden, y: u8) {}

        pub(crate) fn restricted_fn(x: Hidden) -> bool { unimplemented!() }

        pub fn foo(arg: bool, grarg: Hidden) -> Hidden { unimplemented!() }
    """, """
        |Hidden visibility=private
        |private_fn(Hidden, u8) visibility=private
        |restricted_fn(Hidden) -> bool visibility=restricted
        |foo(bool, Hidden) -> Hidden visibility=public
    """, """
        |foo(bool, Hidden) -> Hidden visibility=public
        |restricted_fn(Hidden) -> bool visibility=restricted
        |Hidden visibility=private
        |private_fn(Hidden, u8) visibility=private
    """)

    fun `test visibility sort with traits`() = test("""
        trait HiddenTrait {
            fn foo(&self) {}
        }

        pub trait VisibleTrait {
            fn foo(&self);
            fn bar(self) -> bool where Self: Sized { unimplemented!()}
        }
    """, """
        |-HiddenTrait visibility=private
        | foo() visibility=none
        |-VisibleTrait visibility=public
        | foo() visibility=none
        | bar() -> bool visibility=none
    """, """
        |-VisibleTrait visibility=public
        | foo() visibility=none
        | bar() -> bool visibility=none
        |-HiddenTrait visibility=private
        | foo() visibility=none
    """)

    fun `test visibility sort with impls`() = test("""
        impl Hidden {
            fn new() -> Self { Self }
            pub fn old() -> Self { Self }
        }

        impl Restricted {
            fn new() -> Self { Self }
            pub fn old() -> Self { Self }
        }

        impl Visible {
            fn new() -> Self { Self }
            pub fn old() -> Self { Self }
        }

        impl HiddenTrait for Visible {}

        impl Clone for Visible {
            fn clone(&self) -> Self { Visible }
        }

        struct Hidden;

        trait HiddenTrait {}

        pub(super) struct Restricted;

        pub struct Visible;
    """, """
        |-Hidden visibility=none
        | new() -> Self visibility=private
        | old() -> Self visibility=public
        |-Restricted visibility=none
        | new() -> Self visibility=private
        | old() -> Self visibility=public
        |-Visible visibility=none
        | new() -> Self visibility=private
        | old() -> Self visibility=public
        |HiddenTrait for Visible visibility=none
        |-Clone for Visible visibility=none
        | clone() -> Self visibility=none
        |Hidden visibility=private
        |HiddenTrait visibility=private
        |Restricted visibility=restricted
        |Visible visibility=public
    """, """
        |Visible visibility=public
        |Restricted visibility=restricted
        |-Hidden visibility=none
        | old() -> Self visibility=public
        | new() -> Self visibility=private
        |-Restricted visibility=none
        | old() -> Self visibility=public
        | new() -> Self visibility=private
        |-Visible visibility=none
        | old() -> Self visibility=public
        | new() -> Self visibility=private
        |HiddenTrait for Visible visibility=none
        |-Clone for Visible visibility=none
        | clone() -> Self visibility=none
        |Hidden visibility=private
        |HiddenTrait visibility=private
    """)

    fun `test visibility sort with consts`() = test("""
        const PRIV: bool = false;

        pub(super) const SUP: u8 = 0;

        pub const PUB: u8 = {
            const INNER_PRIV: u32 = 2;
            pub const INNER_PUB: u32 = 1;

            INNER_PRIV as _
        };
    """, """
        |PRIV: bool visibility=private
        |SUP: u8 visibility=restricted
        |PUB: u8 visibility=public
    """, """
        |PUB: u8 visibility=public
        |SUP: u8 visibility=restricted
        |PRIV: bool visibility=private
    """)

    fun `test visibility sort with types`() = test("""
        type Private = bool;

        pub(super) type Sup = bool;

        pub type Pub = Private;
    """, """
        |Private visibility=private
        |Sup visibility=restricted
        |Pub visibility=public
    """, """
        |Pub visibility=public
        |Sup visibility=restricted
        |Private visibility=private
    """)

    fun `test visibility sort with macros`() = test("""
        struct Foo;

        macro_rules! mac {
            ($ v: vis $ T: ident) => {
                $ v struct $ T;
            };
        }

        mac!(Private);
        mac!(pub(super) Super);
        mac!(pub Pub);
    """, """
        |Foo visibility=private
        |mac visibility=none
        |Private visibility=private
        |Super visibility=restricted
        |Pub visibility=public
    """, """
        |Pub visibility=public
        |Super visibility=restricted
        |Foo visibility=private
        |Private visibility=private
        |mac visibility=none
    """)

    fun `test visibility sort with modules`() = test("""
        mod priv_mod {
            mod inner_priv_mod {
                const C: bool = true;

                pub type T = u8;
            }

            pub mod inner_pub_mod {}

            pub(crate) mod inner_crate_mod {}
        }

        pub(crate) mod crate_mod {
            mod inner_priv_mod {
                const C: bool = true;

                pub type T = u8;
            }

            pub mod inner_pub_mod {}

            pub(crate) mod inner_crate_mod {}
        }

        pub mod pub_mod {
            mod inner_priv_mod {
                const C: bool = true;

                pub type T = u8;
            }

            pub mod inner_pub_mod {}

            pub(crate) mod inner_crate_mod {}
        }
    """, """
        |-priv_mod visibility=private
        | -inner_priv_mod visibility=private
        |  C: bool visibility=private
        |  T visibility=public
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
        |-crate_mod visibility=restricted
        | -inner_priv_mod visibility=private
        |  C: bool visibility=private
        |  T visibility=public
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
        |-pub_mod visibility=public
        | -inner_priv_mod visibility=private
        |  C: bool visibility=private
        |  T visibility=public
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
    """, """
        |-pub_mod visibility=public
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
        | -inner_priv_mod visibility=private
        |  T visibility=public
        |  C: bool visibility=private
        |-crate_mod visibility=restricted
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
        | -inner_priv_mod visibility=private
        |  T visibility=public
        |  C: bool visibility=private
        |-priv_mod visibility=private
        | inner_pub_mod visibility=public
        | inner_crate_mod visibility=restricted
        | -inner_priv_mod visibility=private
        |  T visibility=public
        |  C: bool visibility=private
    """)

    private fun test(@Language("Rust") code: String, disabled: String, enabled: String) {
        val wrapTree = { tree: String ->
            val withIndentAndMargin = tree.trimMargin().prependIndent("|  ")
            """
            |-main.rs visibility=none
            | -foo visibility=private
            $withIndentAndMargin
            """
        }
        doTest("mod foo { ${code.trimIndent()} }", wrapTree(disabled), wrapTree(enabled))
    }
}


class RsCombinedVisibilityAlphaSorterTest: RsStructureViewTestBase() {
    fun `test visibility and alpha sort together`() = doTestSingleAction("""
        struct Foo;
        pub struct ZarPub;
        pub struct BarPub {
            foo: bool,
            pub foo_pub: bool,
            pub bar_pub: bool,
            bar: bool,
            pub omnomnom: (),
        }
        struct Zar;
        struct Bar;
        pub struct FooPub;
    """, """
        |-main.rs visibility=none
        | -BarPub visibility=public
        |  bar_pub: bool visibility=public
        |  foo_pub: bool visibility=public
        |  omnomnom: () visibility=public
        |  bar: bool visibility=private
        |  foo: bool visibility=private
        | FooPub visibility=public
        | ZarPub visibility=public
        | Bar visibility=private
        | Foo visibility=private
        | Zar visibility=private
    """) {
        setActionActive(RsVisibilitySorter.ID, true)
        setActionActive(Sorter.ALPHA_SORTER_ID, true)
    }
}
