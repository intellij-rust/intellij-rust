/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.psi.ProcMacroAttributeTest.TestProcMacroAttribute.*
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.stdext.singleOrFilter

@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class ProcMacroAttributeTest : RsTestBase() {
    fun `test inline mod`() = doTest("""
        #[attr]
        mod foo {}
    """, Attr("attr", 0))

    fun `test mod declaration`() = doTest("""
        #[attr]
        mod foo {}
    """, Attr("attr", 0))

    fun `test fn`() = doTest("""
        #[attr]
        fn foo() {}
    """, Attr("attr", 0))

    fun `test const`() = doTest("""
        #[attr]
        const C: i32 = 0;
    """, Attr("attr", 0))

    fun `test static`() = doTest("""
        #[attr]
        static S: i32 = 0;
    """, Attr("attr", 0))

    fun `test struct`() = doTest("""
        #[attr]
        struct S;
    """, Attr("attr", 0))

    fun `test union`() = doTest("""
        #[attr]
        union U {}
    """, Attr("attr", 0))

    fun `test enum`() = doTest("""
        #[attr]
        enum E {}
    """, Attr("attr", 0))

    fun `test trait`() = doTest("""
        #[attr]
        trait T {}
    """, Attr("attr", 0))

    fun `test type alias`() = doTest("""
        #[attr]
        type T = i32;
    """, Attr("attr", 0))

    fun `test impl`() = doTest("""
        #[attr]
        impl Foo {}
    """, Attr("attr", 0))

    fun `test use`() = doTest("""
        #[attr]
        use foo::bar;
    """, Attr("attr", 0))

    fun `test extern block`() = doTest("""
        #[attr]
        extern {}
    """, Attr("attr", 0))

    fun `test extern crate`() = doTest("""
        #[attr]
        extern crate foo;
    """, Attr("attr", 0))

    fun `test enum variant is not a macro`() = doTest("""
        enum E { #[attr] A }
    """, None)

    fun `test type parameter is not a macro`() = doTest("""
        fn foo<#[attr] T>() {}
    """, None)

    fun `test struct field is not a macro`() = doTest("""
        struct S {
            #[attr]
            field: i32
        }
    """, None)

    fun `test code block is not a macro`() = doTest("""
        fn foo() {
            #[attr]
            {

            };
        }
    """, None)

    fun `test empty attr is not a macro`() = doTest("""
        #[()]
        struct S;
    """, None)

    fun `test built-in attribute is not a macro`() = doTest("""
        #[allow()]
        struct S;
    """, None)

    fun `test inner attribute is not a macro`() = doTest("""
        fn foo() {
            #![attr]
        }
    """, None)

    fun `test rustfmt is not a macro 1`() = doTest("""
        #[rustfmt::foo]
        struct S;
    """, None)

    fun `test rustfmt is not a macro 2`() = doTest("""
        #[rustfmt::foo::bar]
        struct S;
    """, None)

    fun `test clippy is not a macro 1`() = doTest("""
        #[rustfmt::foo]
        struct S;
    """, None)

    fun `test clippy is not a macro 2`() = doTest("""
        #[rustfmt::foo::bar]
        struct S;
    """, None)

    fun `test derive`() = doTest("""
        #[derive(Foo)]
        struct S;
    """, Derive)

    fun `test macro before derive`() = doTest("""
        #[attr]
        #[derive(Foo)]
        struct S;
    """, Attr("attr", 0))

    fun `test attr after derive is not a macro`() = doTest("""
        #[derive(Foo)]
        #[attr]
        struct S;
    """, Derive)

    fun `test 1 built-int attribute before macro`() = doTest("""
        #[allow()]
        #[attr]
        struct S;
    """, Attr("attr", 1))

    fun `test 2 built-int attribute before macro`() = doTest("""
        #[allow()]
        #[allow()]
        #[attr]
        struct S;
    """, Attr("attr", 2))

    fun `test built-int attribute after macro`() = doTest("""
        #[attr]
        #[allow()]
        struct S;
    """, Attr("attr", 0))

    fun `test only first attr is actually a macros`() = doTest("""
        #[attr]
        #[attr]
        struct S;
    """, Attr("attr", 0))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 1`() = doTest("""
        #[cfg_attr(intellij_rust, attr)]
        struct S;
    """, Attr("attr", 0))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 2`() = doTest("""
        #[cfg_attr(intellij_rust, allow(), attr)]
        struct S;
    """, Attr("attr", 1))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 3`() = doTest("""
        #[cfg_attr(intellij_rust, allow())]
        #[attr]
        struct S;
    """, Attr("attr", 1))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr is not accounted 1`() = doTest("""
        #[cfg_attr(not(intellij_rust), attr)]
        struct S;
    """, None)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr is not accounted 2`() = doTest("""
        #[cfg_attr(not(intellij_rust), allow())]
        #[attr]
        struct S;
    """, Attr("attr", 0))

    fun `test custom attribute is not a macro 1`() = doTest("""
        #![register_attr(attr)]

        #[attr]
        struct S;
    """, None)

    fun `test custom attribute is not a macro 2`() = doTest("""
        #![register_attr(attr)]

        #[attr]
        #[attr1]
        struct S;
    """, Attr("attr1", 1))

    fun `test custom tool is not a macro 1`() = doTest("""
        #![register_tool(attr)]

        #[attr::foo]
        struct S;
    """, None)

    fun `test custom tool is not a macro 2`() = doTest("""
        #![register_tool(attr)]

        #[attr::foo::bar]
        struct S;
    """, None)

    @UseNewResolve
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test hardcoded not a macro`() = doTest("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        struct S;
    """, None)

    private fun doTest(@Language("Rust") code: String, expectedAttr: TestProcMacroAttribute) {
        InlineFile(code)
        val item = myFixture.file.descendantsOfType<RsDocAndAttributeOwner>()
            .toList()
            .singleOrFilter { it.rawMetaItems.count() != 0 }
            .singleOrNull()
            ?: error("The code snippet should contain only one item")

        val actualAttr = ProcMacroAttribute.getProcMacroAttribute(item).toTestValue()

        assertEquals(expectedAttr, actualAttr)
    }

    private fun ProcMacroAttribute<RsMetaItem>.toTestValue() =
        when (this) {
            is ProcMacroAttribute.Attr -> Attr(attr.path!!.text, index)
            ProcMacroAttribute.Derive -> Derive
            ProcMacroAttribute.None -> None
        }

    private sealed class TestProcMacroAttribute {
        object None: TestProcMacroAttribute()
        object Derive: TestProcMacroAttribute()
        data class Attr(val attr: String, val index: Int): TestProcMacroAttribute()
    }
}
