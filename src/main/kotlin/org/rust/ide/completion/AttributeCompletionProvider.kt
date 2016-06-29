package org.rust.ide.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.cargo.util.cargoProjectRoot
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.module

object AttributeCompletionProvider : CompletionProvider<CompletionParameters>() {

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    private val onStruct: ElementPattern<PsiElement> = onItem<RustStructItemElement>()

    private val onEnum: ElementPattern<PsiElement> = onItem<RustEnumItemElement>()

    private val onFn: ElementPattern<PsiElement> = onItem<RustFnItemElement>()

    private val onMod: ElementPattern<PsiElement> = onItem<RustModItemElement>()

    private val onStatic: ElementPattern<PsiElement> = onItem<RustStaticItemElement>()

    // TODO
    private val onStaticMut: ElementPattern<PsiElement> = onStatic

    private val onMacro: ElementPattern<PsiElement> = onItem<RustMacroItemElement>()

    private val onTupleStruct: ElementPattern<PsiElement> = psiElement()
        .withSuperParent(2, psiElement().withChild(psiElement<RustStructTupleArgsElement>()))

    private val onCrate: ElementPattern<PsiElement> = psiElement().with(
        object: PatternCondition<PsiElement>("onCrateCondition") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val file = t.containingFile.originalFile.virtualFile
                val crateFile = "${t.module?.cargoProjectRoot?.path}/src/lib.rs"
                return file.path.equals(crateFile)
            }
        })

    private val onExternBlock: ElementPattern<PsiElement> = onItem<RustForeignModItemElement>()

    private val onExternBlockDecl: ElementPattern<PsiElement> =
        onItem<RustForeignFnDeclElement>() or
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

    private val attributes = mapOf(
        onCrate to "crate_name crate_type feature no_builtins no_main no_start no_std plugin recursion_limit",
        onExternCrate to "macro_use macro_reexport no_link",
        onMod to "no_implicit_prelude path macro_use",
        onFn to "main plugin_registrar start test cold naked export_name link_section cfg lang inline",
        onTestFn to "should_panic",
        onStaticMut to "thread_local",
        onExternBlock to "link_args link linked_from",
        onExternBlockDecl to "link_name linkage",
        onStruct to "repr unsafe_no_drop_flags derive",
        onEnum to "repr derive",
        onTrait to "rustc_on_unimplemented",
        onMacro to "macro_export",
        onStatic to "export_name link_section",
        onAnyItem to "no_mangle doc cfg_attr allow warn forbid deny",
        onTupleStruct to "simd",
        onDropFn to "unsafe_destructor_blind_to_params"
    ).flatMap { entry -> entry.value.split(' ').map { attrName -> RustAttribute(attrName, entry.key) }}

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

    private fun onItem(pattern: ElementPattern<out RustOuterAttributeOwner>): ElementPattern<PsiElement> {
        return psiElement().withSuperParent(3, pattern)
    }
}
