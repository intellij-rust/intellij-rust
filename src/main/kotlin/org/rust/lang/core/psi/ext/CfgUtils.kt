/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.ProcMacroAttribute
import org.rust.lang.core.psi.RsMetaItem
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.stdext.withPrevious

val PsiElement.isEnabledByCfg: Boolean get() = isEnabledByCfgInner(null)

/**
 * TODO simplify, remove copy-paste
 *
 * Returns `true` if it [isEnabledByCfg] and not inside an element under attribute procedural macro.
 *
 * A one exception is that it returns `true` for attribute macro itself:
 *
 * ```
 * #[a_macro]  // `true` for the attribute
 * fn foo() {} // `false` for the function
 * ```
 */
val PsiElement.existsAfterExpansion: Boolean
    get() = ancestors.withPrevious().all { (it, cameFrom) ->
        when (it) {
            is RsDocAndAttributeOwner -> it.isEnabledByCfgSelf && run {
                val attr = ProcMacroAttribute.getProcMacroAttribute(it)
                attr !is ProcMacroAttribute.Attr || cameFrom != null && cameFrom.isAncestorOf(attr.attr)
            }
            is RsMetaItem -> !it.isRootMetaItem()
                || it.name == "cfg_attr"
                || it in it.owner?.getQueryAttributes(null)?.metaItems.orEmpty()
            else -> true
        }
    }

fun PsiElement.isEnabledByCfg(crate: Crate): Boolean = isEnabledByCfgInner(crate)

private fun PsiElement.isEnabledByCfgInner(crate: Crate?): Boolean =
    ancestors.all {
        when (it) {
            is RsDocAndAttributeOwner -> it.isEnabledByCfgSelfInner(crate)
            is RsMetaItem -> !it.isRootMetaItem()
                || it.name == "cfg_attr"
                || it in it.owner?.getQueryAttributes(crate)?.metaItems.orEmpty()
            else -> true
        }
    }

val PsiElement.isCfgUnknown: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().any { it.isCfgUnknownSelf }

/** Returns `true` if this attribute is `#[cfg_attr()]` and it is disabled */
val RsAttr.isDisabledCfgAttrAttribute: Boolean
    get() {
        val metaItem = metaItem
        if (metaItem.name != "cfg_attr") return false
        val condition = metaItem.metaItemArgs?.metaItemList?.firstOrNull() ?: return false
        val crate = containingCrate ?: return false
        return CfgEvaluator.forCrate(crate).evaluateCondition(condition) == ThreeValuedLogic.False
    }
