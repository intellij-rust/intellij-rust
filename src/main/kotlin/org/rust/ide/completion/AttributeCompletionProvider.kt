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
        val outerAttrElem = psiElement(RustCompositeElementTypes.OUTER_ATTR)
        val innerAttrElem = psiElement(RustCompositeElementTypes.INNER_ATTR)
        val metaItemElem = psiElement(RustCompositeElementTypes.META_ITEM)
            .andOr(psiElement().withParent(outerAttrElem), psiElement().withParent(innerAttrElem))
        return psiElement().withParent(metaItemElem).withLanguage(RustLanguage);
    }

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    private val onStruct: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustStructItemElement::class.java)
    }

    private val onEnum: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustEnumItemElement::class.java)
    }

    private val onFn: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustFnItemElement::class.java)
    }

    private val onMod: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustModItemElement::class.java)
    }

    private val onStatic: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustStaticItemElement::class.java)
    }

    private val onStaticMut: ElementPattern<PsiElement> get() {
        // TODO
        return onStatic
    }

    private val onMacro: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustMacroItemElement::class.java)
    }

    private val onTupleStruct: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(2, psiElement().withChild(psiElement(RustStructTupleArgsElement::class.java)))
    }

    private val onCrate: ElementPattern<PsiElement> get() {
        val inMain = virtualFile().withName("main.rs")
        val inLib = virtualFile().withName("lib.rs")

        return psiElement()
            .withSuperParent(3, PsiFile::class.java)
            .andOr(psiElement().inVirtualFile(inMain), psiElement().inVirtualFile(inLib))
    }

    private val onExternBlock: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustForeignModItemElement::class.java)
    }

    private val onExternBlockDecl: ElementPattern<PsiElement> get() {
        return psiElement().andOr(
            psiElement().withSuperParent(3, RustForeignFnDeclElement::class.java),
            psiElement().withSuperParent(3, RustForeignStaticDeclElement::class.java),
            psiElement().withSuperParent(3, RustForeignModItemElement::class.java))
    }

    private val onAnyItem: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(2, RustOuterAttrElement::class.java)
    }

    private val onExternCrate: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustExternCrateItemElement::class.java)
    }

    private val onTrait: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, RustTraitItemElement::class.java)
    }

    private val onDropFn: ElementPattern<PsiElement> get() {
        val dropTraitRef = psiElement(RustTraitRefElement::class.java).withText("Drop")
        val implBlock = psiElement(RustImplItemElement::class.java).withChild(dropTraitRef)
        return psiElement().withSuperParent(5, implBlock)
    }

    private val onTestFn: ElementPattern<PsiElement> get() {
        return psiElement().withSuperParent(3, psiElement(RustFnItemElement::class.java)
            .withChild(psiElement(RustOuterAttrElement::class.java).withText("#[test]")))
    }

    private infix fun ElementPattern<PsiElement>.or(pattern: ElementPattern<PsiElement>): ElementPattern<PsiElement> {
       return psiElement().andOr(this, pattern)
    }
}
