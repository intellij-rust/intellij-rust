/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

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
import org.rust.ide.icons.RsIcons
import org.rust.lang.RsLanguage
import org.rust.lang.core.RsPsiPattern.onAnyItem
import org.rust.lang.core.RsPsiPattern.onCrate
import org.rust.lang.core.RsPsiPattern.onDropFn
import org.rust.lang.core.RsPsiPattern.onEnum
import org.rust.lang.core.RsPsiPattern.onExternBlock
import org.rust.lang.core.RsPsiPattern.onExternBlockDecl
import org.rust.lang.core.RsPsiPattern.onExternCrate
import org.rust.lang.core.RsPsiPattern.onFn
import org.rust.lang.core.RsPsiPattern.onMacroDefinition
import org.rust.lang.core.RsPsiPattern.onMod
import org.rust.lang.core.RsPsiPattern.onStatic
import org.rust.lang.core.RsPsiPattern.onStaticMut
import org.rust.lang.core.RsPsiPattern.onStruct
import org.rust.lang.core.RsPsiPattern.onTestFn
import org.rust.lang.core.RsPsiPattern.onTrait
import org.rust.lang.core.RsPsiPattern.onTupleStruct
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.queryAttributes

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
        onStruct to "repr unsafe_no_drop_flags derive()",
        onEnum to "repr derive()",
        onTrait to "rustc_on_unimplemented",
        onMacroDefinition to "macro_export",
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
        val outerAttrElem = psiElement<RsOuterAttr>()
        val innerAttrElem = psiElement<RsInnerAttr>()
        val metaItemElem = psiElement<RsMetaItem>()
            .and(PlatformPatterns.psiElement().withParent(outerAttrElem) or PlatformPatterns.psiElement().withParent(innerAttrElem))
        return PlatformPatterns.psiElement().withParent(metaItemElem).withLanguage(RsLanguage)
    }

    private fun createLookupElement(name: String): LookupElement =
        if (name.endsWith("()")) {
            LookupElementBuilder.create(name.substringBeforeLast("()"))
                .withInsertHandler { context, _ ->
                    context.document.insertString(context.selectionEndOffset, "()")
                    EditorModificationUtil.moveCaretRelatively(context.editor, 1)
                }
        } else {
            LookupElementBuilder.create(name)
        }
            .withIcon(RsIcons.ATTRIBUTE)

    private val PsiElement?.attrMetaItems: Sequence<String>
        get() = if (this is RsDocAndAttributeOwner)
            queryAttributes.metaItems.map { it.identifier.text }
        else
            emptySequence()
}
