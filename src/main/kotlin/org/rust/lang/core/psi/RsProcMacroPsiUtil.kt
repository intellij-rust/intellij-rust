/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.psi.PsiElement
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.KNOWN_DERIVABLE_TRAITS

object RsProcMacroPsiUtil {
    fun canBeInProcMacroCallBody(psiElement: PsiElement): Boolean {
        return psiElement.ancestors.any { it is RsStructOrEnumItemElement && canHaveProcMacroCall(it) }
    }

    private fun canHaveProcMacroCall(item: RsStructOrEnumItemElement): Boolean {
        return item.getTraversedRawAttributes(withCfgAttrAttribute = false)
            .deriveMetaItems
            .any { canBeCustomDerive(it) }
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
}
