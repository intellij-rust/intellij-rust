package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.ProcessingContext
import org.rust.lang.RustLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustFile

object AttributeCompletionProvider : CompletionProvider<CompletionParameters>() {

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    val onStruct: PsiElementPattern.Capture<PsiElement> = onItem<RustStructItemElement>()

    val onEnum: PsiElementPattern.Capture<PsiElement> = onItem<RustEnumItemElement>()

    val onFn: PsiElementPattern.Capture<PsiElement> = onItem<RustFnItemElement>()

    val onMod: PsiElementPattern.Capture<PsiElement> = onItem<RustModItemElement>()

    val onStatic: PsiElementPattern.Capture<PsiElement> = onItem<RustStaticItemElement>()

    val onStaticMut: PsiElementPattern.Capture<PsiElement> = psiElement().with(
        object : PatternCondition<PsiElement>("onStaticMutCondition") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val elem = t.parent?.parent?.parent
                return (elem is RustStaticItemElement) && elem.mut != null
            }
        })

    val onMacro: PsiElementPattern.Capture<PsiElement> = onItem<RustMacroItemElement>()

    val onTupleStruct: PsiElementPattern.Capture<PsiElement> = psiElement()
        .withSuperParent(3, psiElement().withChild(psiElement<RustTupleFieldsElement>()))

    val onCrate: PsiElementPattern.Capture<PsiElement> = psiElement().withSuperParent<PsiFile>(3).with(
        object : PatternCondition<PsiElement>("onCrateCondition") {
            override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
                val file = t.containingFile.originalFile as RustFile
                return file.isCrateRoot
            }
        })

    val onExternBlock: PsiElementPattern.Capture<PsiElement> = onItem<RustForeignModItemElement>()

    val onExternBlockDecl: PsiElementPattern.Capture<PsiElement> =
        onItem<RustForeignFnDeclElement>() or
            onItem<RustForeignStaticDeclElement>() or
            onItem<RustForeignModItemElement>()

    val onAnyItem: PsiElementPattern.Capture<PsiElement> = onItem<RustDocAndAttributeOwner>()

    val onExternCrate: PsiElementPattern.Capture<PsiElement> = onItem<RustExternCrateItemElement>()

    val onTrait: PsiElementPattern.Capture<PsiElement> = onItem<RustTraitItemElement>()

    val onDropFn: PsiElementPattern.Capture<PsiElement> get() {
        val dropTraitRef = psiElement<RustTraitRefElement>().withText("Drop")
        val implBlock = psiElement<RustImplItemElement>().withChild(dropTraitRef)
        return psiElement().withSuperParent(4, implBlock)
    }

    val onTestFn: PsiElementPattern.Capture<PsiElement> = onItem(psiElement<RustFnItemElement>()
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
    ).flatMap { entry -> entry.value.split(' ').map { attrName -> RustAttribute(attrName, entry.key) } }

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val elem = parameters.position.parent?.parent?.parent

        val suggestions = attributes.filter { it.appliesTo.accepts(parameters.position) && elem.attrMetaItems.none { item -> item == it.name }}
            .map { LookupElementBuilder.create(it.name) }
        result.addAllElements(suggestions)
    }

    val elementPattern: ElementPattern<PsiElement> get() {
        val outerAttrElem = psiElement<RustOuterAttrElement>()
        val innerAttrElem = psiElement<RustInnerAttrElement>()
        val metaItemElem = psiElement<RustMetaItemElement>()
            .and(psiElement().withParent(outerAttrElem) or psiElement().withParent(innerAttrElem))
        return psiElement().withParent(metaItemElem).withLanguage(RustLanguage)
    }

    inline fun <reified I : RustDocAndAttributeOwner> onItem(): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent<I>(3)
    }

    private fun onItem(pattern: ElementPattern<out RustDocAndAttributeOwner>): PsiElementPattern.Capture<PsiElement> {
        return psiElement().withSuperParent(3, pattern)
    }

    val PsiElement?.attrMetaItems: Sequence<String>
        get() = if (this is RustDocAndAttributeOwner) {
                    queryAttributes.metaItems.map { it.identifier.text }
                } else {
                    emptySequence()
                }
}
