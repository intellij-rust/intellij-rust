/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.crate.Crate
import org.rust.lang.core.psi.*
import org.rust.lang.utils.evaluation.CfgEvaluator
import org.rust.lang.utils.evaluation.ThreeValuedLogic
import org.rust.stdext.withPrevious

val PsiElement.isEnabledByCfg: Boolean get() = isEnabledByCfgInner(null)

/**
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
    get() = existsAfterExpansion(null)

fun PsiElement.existsAfterExpansion(crate: Crate?): Boolean {
    val status = getCodeStatus(crate)
    return status == RsCodeStatus.CODE || status == RsCodeStatus.CFG_UNKNOWN
}

fun PsiElement.isEnabledByCfg(crate: Crate): Boolean = isEnabledByCfgInner(crate)

private fun PsiElement.isEnabledByCfgInner(crate: Crate?): Boolean {
    val status = getCodeStatus(crate)
    return status == RsCodeStatus.CODE || status == RsCodeStatus.ATTR_PROC_MACRO_CALL
        || status == RsCodeStatus.CFG_UNKNOWN
}

val PsiElement.isCfgUnknown: Boolean
    get() = ancestors.filterIsInstance<RsDocAndAttributeOwner>().any { it.isCfgUnknownSelf }

/**
 * Please, prefer using [existsAfterExpansion] or [isEnabledByCfg].
 *
 * See tests in `RsCodeStatusTest`
 */
fun PsiElement.getCodeStatus(crate: Crate?): RsCodeStatus {
    var result = RsCodeStatus.CODE
    for ((it, cameFrom) in stubAncestors.withPrevious().toList().asReversed()) {
        when (it) {
            is RsDocAndAttributeOwner -> {
                when (it.evaluateCfg(crate)) {
                    ThreeValuedLogic.False -> return RsCodeStatus.CFG_DISABLED
                    ThreeValuedLogic.Unknown -> result = RsCodeStatus.CFG_UNKNOWN
                    ThreeValuedLogic.True -> Unit
                }
                if (it is RsAttrProcMacroOwner) {
                    val attr = ProcMacroAttribute.getProcMacroAttribute(it, explicitCrate = crate)
                    if (attr is ProcMacroAttribute.Attr && (cameFrom == null || !cameFrom.isAncestorOf(attr.attr))) {
                        return RsCodeStatus.ATTR_PROC_MACRO_CALL
                    }
                }
            }
            is RsMetaItem -> {
                if (
                    it.isRootMetaItem()
                    && it.name != "cfg_attr"
                    && it !in it.owner?.getQueryAttributes(explicitCrate = crate)?.metaItems.orEmpty()
                ) {
                    return RsCodeStatus.CFG_DISABLED
                }
            }
        }
    }

    return result
}

/**
 * `#[cfg(test)]` or `#[test]`
 */
@Suppress("KDocUnresolvedReference")
val PsiElement.isUnderCfgTest: Boolean
    get() = ancestorOrSelf<RsElement>()?.isUnderCfgTest == true

private val RsElement.isUnderCfgTest: Boolean
    get() {
        val crate = containingCrate
        val trueEvaluator = LazyCfgEvaluator.NonLazy(CfgEvaluator.forCrate(crate, true, ThreeValuedLogic.True))
        val falseEvaluator = LazyCfgEvaluator.NonLazy(CfgEvaluator.forCrate(crate, true, ThreeValuedLogic.False))

        fun isUnderCfgTestSelf(element: RsDocAndAttributeOwner): Boolean {
            val trueValue = element.evaluateCfg(trueEvaluator)
            val falseValue = element.evaluateCfg(falseEvaluator)
            return trueValue == ThreeValuedLogic.True && falseValue == ThreeValuedLogic.False
        }

        for (ancestor in contexts) {
            if (ancestor is RsFunction && ancestor.isTest) return true
            if (ancestor is RsMod) {
                return ancestor.superMods.any { mod ->
                    when (mod) {
                        is RsModItem -> isUnderCfgTestSelf(mod)
                        is RsFile -> isUnderCfgTestSelf(mod) || mod.declaration?.let { isUnderCfgTestSelf(it) } == true
                        else -> false
                    }
                }
            }
            if (ancestor is RsDocAndAttributeOwner) {
                if (isUnderCfgTestSelf(ancestor)) {
                    return true
                }
            }
        }

        return false
    }

/**
 * See tests in `RsCodeStatusTest`
 * @see getCodeStatus
 */
enum class RsCodeStatus {
    /**
     * ```rust
     * #[cfg(feature = "foo")]
     * fn foo() {}
     * ```
     */
    CFG_DISABLED,

    /**
     * ```rust
     * #[cfg(some_unknown_cfg)]
     * fn foo() {}
     * ```
     *
     * @see org.rust.lang.core.crate.Crate.evaluateUnknownCfgToFalse
     */
    CFG_UNKNOWN,

    /**
     * ```rust
     * use my_proc_macro_crate::my_proc_macro;
     *
     * #[my_proc_macro]
     * fn foo() {}
     * ```
     */
    ATTR_PROC_MACRO_CALL,

    /**
     * A regular Rust code
     */
    CODE
}

/** Returns `true` if this attribute is `#[cfg_attr()]` and it is disabled */
fun RsAttr.isDisabledCfgAttrAttribute(crate: Crate): Boolean {
    val metaItem = metaItem
    if (metaItem.name != "cfg_attr") return false
    val condition = metaItem.metaItemArgs?.metaItemList?.firstOrNull() ?: return false
    return CfgEvaluator.forCrate(crate).evaluateCondition(condition) == ThreeValuedLogic.False
}
