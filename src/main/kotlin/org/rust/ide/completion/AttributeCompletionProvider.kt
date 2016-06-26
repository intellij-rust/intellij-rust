package org.rust.ide.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PlatformPatterns.virtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*

object AttributeCompletionProvider : CompletionProvider<CompletionParameters>() {

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    private val onStruct: ElementPattern<PsiElement> = onItem<RustStructItemElement>()

    private val onEnum: ElementPattern<PsiElement> = onItem<RustEnumItemElement>()

    private val onFn: ElementPattern<PsiElement> = onItem<RustFnItemElement>()

    private val onMod: ElementPattern<PsiElement> = onItem<RustModItemElement>()

    private val onStatic: ElementPattern<PsiElement> = onItem<RustStaticItemElement>()

    // TODO
    private val onStaticMut: ElementPattern<PsiElement> = onStatic

    private val onMacro: ElementPattern<PsiElement> = onCItem<RustMacroItemElement>()

    private val onTupleStruct: ElementPattern<PsiElement> = psiElement()
        .withSuperParent(2, psiElement().withChild(psiElement<RustStructTupleArgsElement>()))

    private val onCrate: ElementPattern<PsiElement> get() {
        val inMain = virtualFile().withName("main.rs")
        val inLib = virtualFile().withName("lib.rs")

        return psiElement()
            .withSuperParent<PsiFile>(3)
            .and(psiElement().inVirtualFile(inMain) or psiElement().inVirtualFile(inLib))
    }

    private val onExternBlock: ElementPattern<PsiElement> = onItem<RustForeignModItemElement>()

    private val onExternBlockDecl: ElementPattern<PsiElement> =
        onCItem<RustForeignFnDeclElement>() or
        onItem<RustForeignStaticDeclElement>() or
        onItem<RustForeignModItemElement>()

    private val onAnyItem: ElementPattern<PsiElement> = psiElement().withSuperParent<RustOuterAttrElement>(2)

    private val onExternCrate: ElementPattern<PsiElement> = onItem<RustExternCrateItemElement>()

    private val onTrait: ElementPattern<PsiElement> = onItem<RustTraitItemElement>()

    private val onDropFn: ElementPattern<PsiElement> get() {
        val dropTraitRef = psiElement<RustTraitRefElement>().withText("Drop")
        val implBlock = psiElement<RustImplItemElement>().withChild(dropTraitRef)
        return psiElement().withSuperParent(5, implBlock)
    }

    private val onTestFn: ElementPattern<PsiElement> = onItem(psiElement<RustFnItemElement>()
        .withChild(psiElement<RustOuterAttrElement>().withText("#[test]")))

    private val attributes = listOf(
        RustAttribute("crate_name", onCrate),
        RustAttribute("crate_type", onCrate),
        RustAttribute("feature", onCrate),
        RustAttribute("no_builtins", onCrate),
        RustAttribute("no_main", onCrate),
        RustAttribute("no_start", onCrate),
        RustAttribute("no_std", onCrate),
        RustAttribute("plugin", onCrate), // Feature gated as of 1.9
        RustAttribute("recursion_limit", onCrate),
        RustAttribute("no_implicit_prelude", onMod),
        RustAttribute("path", onMod),
        RustAttribute("main", onFn),
        RustAttribute("plugin_registrar", onFn),
        RustAttribute("start", onFn),
        RustAttribute("test", onFn),
        RustAttribute("should_panic", onTestFn),
        RustAttribute("cold", onFn),
        RustAttribute("naked", onFn),
        RustAttribute("thread_local", onStaticMut),
        RustAttribute("link_args", onExternBlock), // Feature gated as of 1.9
        RustAttribute("link", onExternBlock),
        RustAttribute("linked_from", onExternBlock), // Feature gated as of 1.9
        RustAttribute("link_name", onExternBlockDecl),
        RustAttribute("linkage", onExternBlockDecl),
        RustAttribute("repr", onStruct or onEnum),
        RustAttribute("macro_use", onMod or onExternCrate),
        RustAttribute("macro_reexport", onExternCrate),
        RustAttribute("macro_export", onMacro),
        RustAttribute("no_link", onExternCrate),
        RustAttribute("export_name", onStatic or onFn),
        RustAttribute("link_section", onStatic or onFn),
        RustAttribute("no_mangle", onAnyItem),
        RustAttribute("simd", onTupleStruct), // Feature gated as of 1.9
        RustAttribute("unsafe_destructor_blind_to_params", onDropFn), // Feature gated as of 1.9
        RustAttribute("unsafe_no_drop_flag", onStruct), // Feature gated as of 1.9
        RustAttribute("doc", onAnyItem),
        RustAttribute("rustc_on_unimplemented", onTrait), // Feature gated as of 1.9
        RustAttribute("cfg", onFn),
        RustAttribute("cfg_attr", onAnyItem),
        RustAttribute("allow", onAnyItem),
        RustAttribute("warn", onAnyItem),
        RustAttribute("forbid", onAnyItem),
        RustAttribute("deny", onAnyItem),
        RustAttribute("lang", onFn),
        RustAttribute("inline", onFn),
        RustAttribute("derive", onStruct or onEnum))

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val suggestions = attributes.filter { it.appliesTo.accepts(parameters.position)}
            .map { LookupElementBuilder.create(it.name) }
        result.addAllElements(suggestions)
    }

    val elementPattern: ElementPattern<PsiElement> get() {
        val outerAttrElem = psiElement<RustOuterAttrElement>()
        val innerAttrElem = psiElement<RustInnerAttrElement>()
        val metaItemElem = psiElement<RustMetaItemElement>()
            .and(psiElement().withParent(outerAttrElem) or psiElement().withParent(innerAttrElem))
        return psiElement().withParent(metaItemElem).withLanguage(RustLanguage);
    }

    inline fun <reified I: RustOuterAttributeOwner> onItem(): ElementPattern<PsiElement> {
        return psiElement().withSuperParent<I>(3)
    }

    // TODO: remove once https://github.com/intellij-rust/intellij-rust/issues/492 is fixed
    inline fun <reified I: RustCompositeElement> onCItem(): ElementPattern<PsiElement> {
        return psiElement().withSuperParent<I>(3)
    }

    private fun onItem(pattern: ElementPattern<out RustOuterAttributeOwner>): ElementPattern<PsiElement> {
        return psiElement().withSuperParent(3, pattern)
    }
}
