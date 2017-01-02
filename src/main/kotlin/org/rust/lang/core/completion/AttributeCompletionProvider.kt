package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RustIcons
import org.rust.lang.RustLanguage
import org.rust.lang.core.RustPsiPattern.onAnyItem
import org.rust.lang.core.RustPsiPattern.onCrate
import org.rust.lang.core.RustPsiPattern.onDropFn
import org.rust.lang.core.RustPsiPattern.onEnum
import org.rust.lang.core.RustPsiPattern.onExternBlock
import org.rust.lang.core.RustPsiPattern.onExternBlockDecl
import org.rust.lang.core.RustPsiPattern.onExternCrate
import org.rust.lang.core.RustPsiPattern.onFn
import org.rust.lang.core.RustPsiPattern.onMacro
import org.rust.lang.core.RustPsiPattern.onMod
import org.rust.lang.core.RustPsiPattern.onStatic
import org.rust.lang.core.RustPsiPattern.onStaticMut
import org.rust.lang.core.RustPsiPattern.onStruct
import org.rust.lang.core.RustPsiPattern.onTestFn
import org.rust.lang.core.RustPsiPattern.onTrait
import org.rust.lang.core.RustPsiPattern.onTupleStruct
import org.rust.lang.core.psi.*

object AttributeCompletionProvider : CompletionProvider<CompletionParameters>() {

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    private val attributes = mapOf(
        onCrate to "crate_name crate_type feature() no_builtins no_main no_start no_std plugin recursion_limit",
        onExternCrate to "macro_use macro_reexport no_link",
        onMod to "no_implicit_prelude path macro_use",
        onFn to "main plugin_registrar start test cold naked export_name link_section lang inline",
        onTestFn to "should_panic",
        onStaticMut to "thread_local",
        onExternBlock to "link_args link() linked_from",
        onExternBlockDecl to "link_name linkage",
        onStruct to "repr unsafe_no_drop_flags derive",
        onEnum to "repr derive()",
        onTrait to "rustc_on_unimplemented",
        onMacro to "macro_export",
        onStatic to "export_name link_section",
        onAnyItem to "no_mangle doc cfg() cfg_attr() allow() warn() forbid() deny()",
        onTupleStruct to "simd",
        onDropFn to "unsafe_destructor_blind_to_params"
    ).flatMap { entry -> entry.value.split(' ').map { attrName -> RustAttribute(attrName, entry.key) } }

    override fun addCompletions(parameters: CompletionParameters,
                                context: ProcessingContext?,
                                result: CompletionResultSet) {

        val elem = parameters.position.parent?.parent?.parent

        val suggestions = attributes
            .filter { it.appliesTo.accepts(parameters.position) && elem.attrMetaItems.none { item -> item == it.name } }
            .map { createLookupElement(it.name) }
        result.addAllElements(suggestions)
    }

    val elementPattern: ElementPattern<PsiElement> get() {
        val outerAttrElem = psiElement<RustOuterAttrElement>()
        val innerAttrElem = psiElement<RustInnerAttrElement>()
        val metaItemElem = psiElement<RustMetaItemElement>()
            .and(PlatformPatterns.psiElement().withParent(outerAttrElem) or PlatformPatterns.psiElement().withParent(innerAttrElem))
        return PlatformPatterns.psiElement().withParent(metaItemElem).withLanguage(RustLanguage)
    }

    private fun createLookupElement(name: String): LookupElement =
        if (name.endsWith("()")) {
            LookupElementBuilder.create(name.substringBeforeLast("()"))
                .withInsertHandler { context, lookupElement ->
                    context.document.insertString(context.selectionEndOffset, "()")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 1)
                }
        } else {
            LookupElementBuilder.create(name)
        }
            .withIcon(RustIcons.ATTRIBUTE)

    private val PsiElement?.attrMetaItems: Sequence<String>
        get() = if (this is RustDocAndAttributeOwner)
            queryAttributes.metaItems.map { it.identifier.text }
        else
            emptySequence()
}
