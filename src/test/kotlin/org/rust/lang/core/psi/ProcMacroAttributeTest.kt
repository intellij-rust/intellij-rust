/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments.ATTR_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.DERIVE_PROC_MACROS
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.psi.ProcMacroAttributeTest.TestProcMacroAttribute.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.stubs.RsAttrProcMacroOwnerStub
import org.rust.stdext.singleOrFilter

/**
 * A test for detecting proc macro attributes on items. See [ProcMacroAttribute]
 */
@ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class ProcMacroAttributeTest : RsTestBase() {
    fun `test inline mod`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        mod foo {}
    """, Attr("attr_as_is", 0))

    fun `test mod declaration is not a macro`() = checkNotAProcMacroOwner("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        mod foo;
    """)

    fun `test fn`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        fn foo() {}
    """, Attr("attr_as_is", 0))

    fun `test const`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        const C: i32 = 0;
    """, Attr("attr_as_is", 0))

    fun `test static`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        static S: i32 = 0;
    """, Attr("attr_as_is", 0))

    fun `test struct`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 0))

    fun `test union`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        union U {}
    """, Attr("attr_as_is", 0))

    fun `test enum`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        enum E {}
    """, Attr("attr_as_is", 0))

    fun `test trait`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        trait T {}
    """, Attr("attr_as_is", 0))

    fun `test trait alias`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        trait T = Foo;
    """, Attr("attr_as_is", 0))

    fun `test type alias`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        type T = i32;
    """, Attr("attr_as_is", 0))

    fun `test impl`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        impl Foo {}
    """, Attr("attr_as_is", 0))

    fun `test use`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        use foo::bar;
    """, Attr("attr_as_is", 0))

    fun `test extern block`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        extern {}
    """, Attr("attr_as_is", 0))

    fun `test extern crate`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        extern crate foo;
    """, Attr("attr_as_is", 0))

    fun `test macro_rules`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        macro_rules! foo {}
    """, Attr("attr_as_is", 0))

    fun `test macro 2`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        macro foo() {}
    """, Attr("attr_as_is", 0))

    fun `test macro call`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        foo!();
    """, Attr("attr_as_is", 0))

    fun `test enum variant is not a macro`() = checkNotAProcMacroOwner("""
        use test_proc_macros::attr_as_is;

        enum E { #[attr_as_is] A }
    """)

    fun `test type parameter is not a macro`() = checkNotAProcMacroOwner("""
        use test_proc_macros::attr_as_is;

        fn foo<#[attr_as_is] T>() {}
    """)

    fun `test struct field is not a macro`() = checkNotAProcMacroOwner("""
        use test_proc_macros::attr_as_is;

        struct S {
            #[attr_as_is]
            field: i32
        }
    """)

    fun `test code block is not a macro`() = checkNotAProcMacroOwner("""
        use test_proc_macros::attr_as_is;

        fn foo() {
            #[attr_as_is]
            {

            };
        }
    """)

    fun `test empty attr is not a macro`() = doTest("""
        #[()]
        struct S;
    """, None)

    fun `test built-in attribute is not a macro`() = doTest("""
        #[allow()]
        struct S;
    """, None)

    fun `test inner attribute is not a macro`() = doTest("""
        use test_proc_macros::attr_as_is;

        fn foo() {
            #![attr_as_is]
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
        #[clippy::foo]
        struct S;
    """, None)

    fun `test clippy is not a macro 2`() = doTest("""
        #[clippy::foo::bar]
        struct S;
    """, None)

    fun `test derive`() = doTest("""
        #[derive(Foo)]
        struct S;
    """, Derive)

    fun `test macro before derive`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        #[derive(Foo)]
        struct S;
    """, Attr("attr_as_is", 0))

    fun `test attr after derive is not a macro`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[derive(Foo)]
        #[attr_as_is]
        struct S;
    """, Derive)

    fun `test 1 built-int attribute before macro`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[allow()]
        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 1))

    fun `test 2 built-int attribute before macro`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[allow()]
        #[allow()]
        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 2))

    fun `test built-int attribute after macro`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        #[allow()]
        struct S;
    """, Attr("attr_as_is", 0))

    fun `test only first attr is actually a macros`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 0))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 1`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[cfg_attr(intellij_rust, attr_as_is)]
        struct S;
    """, Attr("attr_as_is", 0))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 2`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[cfg_attr(intellij_rust, allow(), attr_as_is)]
        struct S;
    """, Attr("attr_as_is", 1))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test enabled cfg_attr is accounted 3`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[cfg_attr(intellij_rust, allow())]
        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 1))

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr is not accounted 1`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[cfg_attr(not(intellij_rust), attr_as_is)]
        struct S;
    """, None)

    @MockAdditionalCfgOptions("intellij_rust")
    fun `test disabled cfg_attr is not accounted 2`() = doTest("""
        use test_proc_macros::attr_as_is;

        #[cfg_attr(not(intellij_rust), allow())]
        #[attr_as_is]
        struct S;
    """, Attr("attr_as_is", 0))

    fun `test custom attribute is not a macro 1`() = doTest("""
        #![register_attr(attr_as_is)]

        use test_proc_macros::attr_as_is;

        #[attr_as_is]
        struct S;
    """, None)

    fun `test custom attribute is not a macro 2`() = doTest("""
        #![register_attr(attr_as_is)]

        use test_proc_macros::attr_as_is;
        use test_proc_macros::attr_as_is as attr_as_is1;

        #[attr_as_is]
        #[attr_as_is1]
        struct S;
    """, Attr("attr_as_is1", 1))

    fun `test custom tool is not a macro 1`() = doTest("""
        #![register_tool(test_proc_macros)]

        #[test_proc_macros::attr_as_is]
        struct S;
    """, None)

    fun `test custom tool is not a macro 2`() = doTest("""
        #![register_tool(attr)]

        #[attr::foo::bar]
        struct S;
    """, None)

    fun `test hardcoded identity macro`() = doTest("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        struct S;
    """, None)

    fun `test hardcoded identity macro 2`() = doTest("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        enum E {}
    """, Attr("attr_hardcoded_not_a_macro", 0))

    fun `test hardcoded identity macro 3`() = doTest("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        mod m {}
    """, Attr("attr_hardcoded_not_a_macro", 0))

    fun `test 2 hardcoded identity macros`() = doTest("""
        use test_proc_macros::attr_hardcoded_not_a_macro;

        #[attr_hardcoded_not_a_macro]
        #[attr_hardcoded_not_a_macro]
        struct S;
    """, None)

    fun `test hardcoded identity macro and then a non-hardcoded attr macro`() = doTest("""
        use test_proc_macros::*;

        #[attr_hardcoded_not_a_macro]
        #[attr_as_is]
        struct S;
    """, Attr("attr_hardcoded_not_a_macro", 0))

    fun `test hardcoded identity macro and then a non-hardcoded derive macro`() = doTest("""
        use test_proc_macros::*;

        #[attr_hardcoded_not_a_macro]
        #[derive(DeriveImplForFoo)]
        struct S;
    """, Attr("attr_hardcoded_not_a_macro", 0))

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS)
    fun `test not a macro if proc macro expansion is disabled`() {
        setExperimentalFeatureEnabled(PROC_MACROS, false, testRootDisposable)
        doTest("""
            use test_proc_macros::attr_as_is;

            #[attr_as_is]
            mod foo {}
        """, None)
    }

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS)
    fun `test not a macro if attr macro expansion is disabled 1`() {
        setExperimentalFeatureEnabled(ATTR_PROC_MACROS, false, testRootDisposable)
        doTest("""
            use test_proc_macros::attr_as_is;

            #[attr_as_is]
            mod foo {}
        """, None)
    }

    @WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, DERIVE_PROC_MACROS)
    fun `test not a macro if attr macro expansion is disabled 2`() {
        setExperimentalFeatureEnabled(ATTR_PROC_MACROS, false, testRootDisposable)
        doTest("""
            use test_proc_macros::attr_as_is;

            #[attr_as_is]
            #[derive(Foo)]
            struct S;
        """, Derive)
    }

    private fun doTest(@Language("Rust") code: String, expectedAttr: TestProcMacroAttribute) {
        InlineFile(code)
        val item = findItemWithAttrs()

        check(item is RsAttrProcMacroOwner) { "${item.javaClass} must implement `RsAttrProcMacroOwner`" }

        val actualAttr = (item as? RsAttrProcMacroOwner)?.procMacroAttribute?.toTestValue()
            ?: None

        assertEquals(expectedAttr, actualAttr)

        @Suppress("UNCHECKED_CAST")
        val itemElementType = item.elementType as IStubElementType<*, PsiElement>
        val stub = itemElementType.createStub(item, null)
        if (stub is RsAttrProcMacroOwnerStub && actualAttr is Attr) {
            assertEquals(item.stubbedText, stub.stubbedText)
            assertEquals(item.startOffset, stub.startOffset)
            assertEquals(item.endOfAttrsOffset, stub.endOfAttrsOffset)
        }
    }

    private fun checkNotAProcMacroOwner(@Language("Rust") code: String) {
        InlineFile(code)
        val item = findItemWithAttrs()
        check(item !is RsAttrProcMacroOwner) { "${item.javaClass} must NOT implement `RsAttrProcMacroOwner`" }
    }

    private fun findItemWithAttrs(): RsOuterAttributeOwner = (myFixture.file.descendantsOfType<RsOuterAttributeOwner>()
        .toList()
        .singleOrFilter { it.rawMetaItems.count() != 0 }
        .singleOrNull()
        ?: error("The code snippet must contain only one item"))

    private fun ProcMacroAttribute<RsMetaItem>.toTestValue() =
        when (this) {
            is ProcMacroAttribute.Attr -> Attr(attr.path!!.text, index)
            is ProcMacroAttribute.Derive -> Derive
            null -> None
        }

    private sealed class TestProcMacroAttribute {
        object None: TestProcMacroAttribute() {
            override fun toString(): String = "None"
        }
        object Derive: TestProcMacroAttribute() {
            override fun toString(): String = "Derive"
        }
        data class Attr(val attr: String, val index: Int): TestProcMacroAttribute()
    }
}
