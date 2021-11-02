/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.macros.proc.ProcMacroApplicationService
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KNOWN_DERIVABLE_TRAITS
import org.rust.lang.core.stubs.*
import org.rust.lang.core.stubs.common.RsAttrProcMacroOwnerPsiOrStub
import org.rust.lang.core.stubs.common.RsMetaItemPsiOrStub

object RsProcMacroPsiUtil {
    fun canBeInProcMacroCallBody(psiElement: PsiElement): Boolean {
        if (!ProcMacroApplicationService.isEnabled()) return false
        return psiElement.ancestors.any { it is RsAttrProcMacroOwner && canHaveProcMacroCall(it) }
    }

    private fun canHaveProcMacroCall(item: RsAttrProcMacroOwner): Boolean {
        val checkDerives = item is RsStructOrEnumItemElement
        for (meta in item.getTraversedRawAttributes(withCfgAttrAttribute = false).metaItems) {
            if (canBeProcMacroAttributeCall(meta)) return true
            if (checkDerives && meta.name == "derive") {
                if (meta.metaItemArgs?.metaItemList.orEmpty().any(::canBeCustomDerive)) return true
            }
        }
        return false
    }

    /**
     * Returns true if this [metaItem] can be a custom (non-std) derive macro call:
     * ```
     * #[derive(Clone, Foo)]
     *               //^ custom derive
     * ```
     * The function is syntax-based, i.e. it doesn't perform name resolution or index lookup
     */
    fun canBeCustomDerive(metaItem: RsMetaItem): Boolean {
        val isDerive = RsPsiPattern.derivedTraitMetaItem.accepts(metaItem)
        return isDerive && canBeCustomDeriveWithoutContextCheck(metaItem)
    }

    fun canBeCustomDeriveWithoutContextCheck(metaItem: RsMetaItemPsiOrStub) =
        KNOWN_DERIVABLE_TRAITS[metaItem.name]?.isStd != true

    private fun canBeProcMacroAttributeCall(
        metaItem: RsMetaItem,
        customAttrs: CustomAttributes = CustomAttributes.EMPTY
    ): Boolean {
        val context = ProcessingContext()
        if (!metaItem.isRootMetaItem(context)) return false
        val containingAttr = context[RsPsiPattern.META_ITEM_ATTR]
        if (containingAttr !is RsOuterAttr) return false
        if (containingAttr.owner !is RsAttrProcMacroOwner) return false

        return canBeProcMacroAttributeCallWithoutContextCheck(metaItem, customAttrs)
    }

    fun canBeProcMacroAttributeCallWithoutContextCheck(
        metaItem: RsMetaItemPsiOrStub,
        customAttrs: CustomAttributes
    ): Boolean {
        val name = metaItem.name
        return if (name == null) { // A possible multi-segment path `#[foo::bar]`
            val base = metaItem.path?.basePath()?.referenceName ?: return false
            base !in RS_BUILTIN_TOOL_ATTRIBUTES && base !in customAttrs.customTools
        } else {
            name !in RS_BUILTIN_ATTRIBUTES && name !in customAttrs.customAttrs
        }
    }

    /**
     * Returns true if this [metaItem] can be an attribute proc macro call or
     * a custom (non-std) derive macro call:
     * ```
     * #[foobar]
     * //^ attribute proc macro call
     * struct A;
     *
     * #[derive(Clone, Foo)]
     *               //^ custom derive
     * struct B;
     * ```
     * The function is syntax-based, i.e. it doesn't perform name resolution or index lookup
     */
    fun canBeProcMacroCall(metaItem: RsMetaItem): Boolean {
        return canBeCustomDerive(metaItem) || canBeProcMacroAttributeCall(metaItem)
    }

    /**
     * Sometimes we want to ignore a proc macro attribute, i.e. leave the item as is.
     * Due to low level name resolution engine limitations, we can't do such "fallback" for
     * all kinds of items
     */
    fun canFallBackAttrMacroToOriginalItem(item: RsAttrProcMacroOwnerStub): Boolean =
        item is RsImplItemStub || item is RsNamedStub && (
            item !is RsEnumItemStub
                && item !is RsModItemStub
                && item !is RsMacroStub
                && item !is RsMacro2Stub
            )

    /** @see canFallBackAttrMacroToOriginalItem */
    fun canFallBackAttrMacroToOriginalItem(item: RsAttrProcMacroOwner): Boolean =
        item is RsImplItem || item is RsNamedElement && (
            item !is RsEnumItem
                && item !is RsModItem
                && item !is RsMacroDefinitionBase
            )

    fun canOwnDeriveAttrs(item: RsAttrProcMacroOwnerPsiOrStub<*>): Boolean =
        item is RsStructOrEnumItemElement || item is RsStructItemStub || item is RsEnumItemStub
}
