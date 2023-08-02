/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.RsPsiPattern.META_ITEM_ATTR
import org.rust.lang.core.RsPsiPattern.onAnyItem
import org.rust.lang.core.RsPsiPattern.onCrate
import org.rust.lang.core.RsPsiPattern.onEnum
import org.rust.lang.core.RsPsiPattern.onExternBlock
import org.rust.lang.core.RsPsiPattern.onExternBlockDecl
import org.rust.lang.core.RsPsiPattern.onExternCrate
import org.rust.lang.core.RsPsiPattern.onFn
import org.rust.lang.core.RsPsiPattern.onMacro
import org.rust.lang.core.RsPsiPattern.onMod
import org.rust.lang.core.RsPsiPattern.onProcMacroFn
import org.rust.lang.core.RsPsiPattern.onStatic
import org.rust.lang.core.RsPsiPattern.onStaticMut
import org.rust.lang.core.RsPsiPattern.onStruct
import org.rust.lang.core.RsPsiPattern.onStructLike
import org.rust.lang.core.RsPsiPattern.onTestFn
import org.rust.lang.core.RsPsiPattern.onTrait
import org.rust.lang.core.RsPsiPattern.onTupleStruct
import org.rust.lang.core.RsPsiPattern.rootMetaItem
import org.rust.lang.core.psi.RsElementTypes
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsDocAndAttributeOwner
import org.rust.lang.core.psi.ext.name
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.psi.ext.queryAttributes
import org.rust.lang.core.psiElement
import org.rust.lang.core.with

object RsAttributeCompletionProvider : RsCompletionProvider() {

    private data class RustAttribute(val name: String, val appliesTo: ElementPattern<PsiElement>)

    private val attributes = mapOf(
        onCrate to "crate_name crate_type feature() no_builtins no_main no_start no_std recursion_limit " +
            "type_length_limit windows_subsystem",
        onExternCrate to "macro_use no_link",
        onMod to "no_implicit_prelude path macro_use",
        onFn to "main start test cold naked export_name link_section lang inline track_caller " +
            "panic_handler must_use target_feature()",
        onTestFn to "should_panic ignore",
        onProcMacroFn to "proc_macro proc_macro_derive() proc_macro_attribute",
        onStaticMut to "thread_local",
        onExternBlock to "link_args link() linked_from",
        onExternBlockDecl to "link_name linkage",
        onStruct to "repr() unsafe_no_drop_flags derive() must_use",
        onEnum to "repr() derive() must_use",
        onTrait to "must_use",
        onMacro to "macro_export",
        onStatic to "export_name link_section used global_allocator",
        onAnyItem to "no_mangle doc cfg() cfg_attr() allow() warn() forbid() deny() deprecated",
        onTupleStruct to "simd",
        onStructLike to "non_exhaustive"
    ).flatMap { entry -> entry.value.split(' ').map { attrName -> RustAttribute(attrName, entry.key) } }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val elem = context[META_ITEM_ATTR]?.owner ?: return

        val suggestions = attributes
            .filter { it.appliesTo.accepts(parameters.position) && elem.attrMetaItems.none { item -> item == it.name } }
            .map { createLookupElement(it.name) }
        result.addAllElements(suggestions)
    }

    override val elementPattern: ElementPattern<PsiElement>
        get() = psiElement(RsElementTypes.IDENTIFIER)
            .withParent(
                psiElement<RsPath>()
                    .with("Unqualified") { it: RsPath -> !it.hasColonColon }
                    .withParent(rootMetaItem)
            )

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
            queryAttributes.metaItems.mapNotNull { it.name }
        else
            emptySequence()
}
