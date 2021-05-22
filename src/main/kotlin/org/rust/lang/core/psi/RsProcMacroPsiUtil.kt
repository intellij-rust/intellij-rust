/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KNOWN_DERIVABLE_TRAITS
import org.rust.lang.core.stubs.RsAttrProcMacroOwnerStub
import org.rust.lang.core.stubs.common.RsAttributeOwnerPsiOrStub
import org.rust.lang.core.stubs.common.RsMetaItemPsiOrStub

object RsProcMacroPsiUtil {
    private val BUILTIN_TOOLS = setOf("rustfmt", "clippy")

    fun canBeInProcMacroCallBody(psiElement: PsiElement): Boolean {
        return psiElement.ancestors.any { it is RsDocAndAttributeOwner && canHaveProcMacroCall(it) }
    }

    // TODO trigger only if proc macro expansion is enabled?
    private fun canHaveProcMacroCall(item: RsDocAndAttributeOwner): Boolean {
        if (!psiTypeCanHaveProcMacroCall(item)) return false
        val checkDerives = item is RsStructOrEnumItemElement
        for (meta in item.getTraversedRawAttributes(withCfgAttrAttribute = false).metaItems) {
            if (canBeProcMacroAttributeCall(meta)) return true
            if (checkDerives && meta.name == "derive") {
                if (meta.metaItemArgs?.metaItemList.orEmpty().any(::canBeCustomDerive)) return true
            }
        }
        return false
    }

    private fun psiTypeCanHaveProcMacroCall(item: RsDocAndAttributeOwner): Boolean {
        return item is RsItemElement // TODO not only RsItemElement
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
        return isDerive && KNOWN_DERIVABLE_TRAITS[metaItem.name]?.isStd != true
    }

    private fun canBeProcMacroAttributeCall(
        metaItem: RsMetaItem,
        customAttrs: CustomAttributes = CustomAttributes.EMPTY
    ): Boolean {
        val context = ProcessingContext()
        if (!metaItem.isRootMetaItem(context)) return false
        val containingAttr = context[RsPsiPattern.META_ITEM_ATTR]
        if (containingAttr !is RsOuterAttr) return false
        val owner = containingAttr.owner
        if (owner == null || !psiTypeCanHaveProcMacroCall(owner)) return false

        return canBeProcMacroAttributeCallWithoutContextCheck(metaItem, customAttrs)
    }

    fun canBeProcMacroAttributeCallWithoutContextCheck(
        metaItem: RsMetaItemPsiOrStub,
        customAttrs: CustomAttributes
    ): Boolean {
        val name = metaItem.name
        return if (name == null) { // A possible multi-segment path `#[foo::bar]`
            val base = metaItem.path?.basePath()?.referenceName ?: return false
            base !in BUILTIN_TOOLS && base !in customAttrs.customTools
        } else {
            name !in RS_BUILTIN_ATTRIBUTES && name !in customAttrs.customAttrs
        }
    }

    fun canBeProcMacroCall(metaItem: RsMetaItem): Boolean {
        return canBeCustomDerive(metaItem) || canBeProcMacroAttributeCall(metaItem)
    }

    fun psiOrStubTypeCanHaveProcMacroCall(owner: RsAttributeOwnerPsiOrStub<*>): Boolean {
        return owner is RsDocAndAttributeOwner && psiTypeCanHaveProcMacroCall(owner) || owner is RsAttrProcMacroOwnerStub
    }
}
