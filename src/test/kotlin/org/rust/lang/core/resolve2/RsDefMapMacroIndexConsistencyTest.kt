/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.ide.experiments.RsExperiments.EVALUATE_BUILD_SCRIPTS
import org.rust.ide.experiments.RsExperiments.PROC_MACROS
import org.rust.lang.core.macros.MacroExpansionScope
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.RsModInfoBase.RsModInfo
import org.rust.openapiext.toPsiFile
import org.rust.stdext.withPrevious

@MinRustcVersion("1.46.0")
@WithExperimentalFeatures(EVALUATE_BUILD_SCRIPTS, PROC_MACROS)
class RsDefMapMacroIndexConsistencyTest : RsTestBase() {
    fun `test simple`() = doTest("""
        use test_proc_macros::*;

        macro_rules! macro0 { () => {} }
        macro0!();
        macro_rules! macro1 { () => {} }
        mod mod1 {}
        macro_rules! macro2 { () => {} }
        mod mod2;
        macro_rules! macro3 { () => {} }
    """, """
        [0, 0] macro macro0
        [0, 1] macro0!()
        [0, 2] macro macro1
        [0, 3] mod mod1
        [0, 4] macro macro2
        [0, 5] mod mod2
        [0, 6] macro macro3
    """)

    @ExpandMacros(MacroExpansionScope.WORKSPACE)
    @ProjectDescriptor(WithProcMacroRustProjectDescriptor::class)
    fun `test proc macros`() = doTest("""
        use test_proc_macros::*;

        macro_rules! macro0 { () => {} }
        #[attr_as_is]
        fn func() {}
        macro_rules! macro1 { () => {} }
        #[derive(DeriveMacroFooThatExpandsToStructFoo, DeriveMacroFooInvocation)]
        // macro_rules! foo { () => { struct Foo; } }
        // foo!()
        struct Struct;
        macro_rules! macro2 { () => {} }
    """, """
        [1, 0] macro macro0
        [1, 1] #[attr_as_is]
        [1, 1, 0] fn func
        [1, 2] macro macro1
        [1, 3] struct Struct
        [1, 3, 0] #[derive(DeriveMacroFooThatExpandsToStructFoo)]
        [1, 3, 0, 0] macro foo
        [1, 3, 1] #[derive(DeriveMacroFooInvocation)]
        [1, 3, 1, 0] foo!()
        [1, 3, 1, 0, 0] struct Foo
        [1, 4] macro macro2
    """)

    fun doTest(@Language("Rust") code: String, itemIndices: String) {
        InlineFile(code)
        val crateRoot = myFixture.findFileInTempDir("main.rs").toPsiFile(myFixture.project) as RsFile
        val info = getModInfo(crateRoot) as RsModInfo

        val expandedItems = mutableListOf<RsElement>()
        crateRoot.processExpandedItemsInternal(withMacroCalls = true) { item, _ ->
            expandedItems += item
            false
        }

        val macroIndicesDuringResolve = expandedItems
            .asSequence()
            .filter {
                it is RsNamedElement && it is RsItemElement || it is RsMacro || it is RsMacroCall || it is RsMetaItem
            }
            .map {
                val name = when (it) {
                    is RsItemElement -> "${it.itemDefKeyword.text} ${it.name!!}"
                    is RsMacro -> "macro ${it.name!!}"
                    is RsMacroCall -> "${it.path.text}!()"
                    is RsMetaItem -> if (RsProcMacroPsiUtil.canBeCustomDerive(it)) {
                        "#[derive(${it.path!!.text})]"
                    } else {
                        "#[${it.path!!.text}]"
                    }
                    else -> error("impossible")
                }
                val index = info.getMacroIndex(it, info.crate)!!
                index to name
            }
            .withPrevious()
            .filter { (i, prev) -> prev == null || !MacroIndex.equals(i.first, prev.first) }
            .map { it.first }
            .toList()
        val macroIndicesDuringBuild = info.defMap.root.legacyMacros.values
            .map { it.single() }
            .filterIsInstance<DeclMacroDefInfo>()
            .sortedBy { it.macroIndex }
            .map { it.macroIndex to "macro ${it.path.name}" }

        fun List<Pair<MacroIndex, String>>.indicesToString(): String =
            joinToString("\n") { (index, name) -> "$index $name" }

        val expectedItemIndices = itemIndices.trimIndent()
        val expectedLegacyMacrosIndices = expectedItemIndices
            .lineSequence()
            .filter {
                it.contains("] macro ")
            }
            .joinToString(separator = "\n")

        assertEquals(expectedItemIndices, macroIndicesDuringResolve.indicesToString())
        assertEquals(expectedLegacyMacrosIndices, macroIndicesDuringBuild.indicesToString())
    }
}
