/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.ide.utils.CallInfo
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.startOffset
import org.rust.stdext.buildList

/** Non-interactive old-fashioned hint */
interface RsPlainHint {
    fun isApplicable(elem: PsiElement): Boolean
    fun provideHints(elem: PsiElement): List<InlayInfo>
    val enabled: Boolean
    val option: Option

    companion object {
        val SMART_HINTING = Option("SMART_HINTING", "Show only smart hints", true)

        fun resolve(element: PsiElement): RsPlainHint? =
            values.find { it.isApplicable(element) }

        val values: Array<RsPlainHint> =
            arrayOf(*RsPlainParameterHint.values(), *RsPlainTypeHint.values())
    }
}

enum class RsPlainParameterHint(desc: String, enabled: Boolean) : RsPlainHint {
    PARAMETER_HINT("Show argument name hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val (callInfo, valueArgumentList) = when (elem) {
                is RsCallExpr -> (CallInfo.resolve(elem) to elem.valueArgumentList)
                is RsMethodCall -> (CallInfo.resolve(elem) to elem.valueArgumentList)
                else -> return emptyList()
            }
            if (!elem.isEnabledByCfg) return emptyList()
            if (callInfo == null) return emptyList()

            val hints = buildList<String> {
                if (callInfo.selfParameter != null && elem is RsCallExpr) {
                    add(callInfo.selfParameter)
                }
                addAll(callInfo.parameters.map { it.pattern })
            }.zip(valueArgumentList.exprList)

            if (smart) {
                if (onlyOneParam(hints, callInfo, elem)) {
                    if (callInfo.methodName?.startsWith("set_") != false) {
                        return emptyList()
                    }
                    if (callInfo.methodName == callInfo.parameters.getOrNull(0)?.pattern) {
                        return emptyList()
                    }
                }
                if (hints.all { (hint, _) -> hint == "_" }) {
                    return emptyList()
                }
                return hints
                    .filter { (hint, arg) -> !arg.text.endsWith(hint) }
                    .map { (hint, arg) -> InlayInfo("$hint:", arg.startOffset) }
            }
            return hints.map { (hint, arg) -> InlayInfo("$hint:", arg.startOffset) }
        }

        private fun onlyOneParam(hints: List<Pair<String, RsExpr>>, callInfo: CallInfo, elem: PsiElement): Boolean {
            if (callInfo.selfParameter != null && elem is RsCallExpr && hints.size == 2) {
                return true
            }
            if (!(callInfo.selfParameter != null && elem is RsCallExpr) && hints.size == 1) {
                return true
            }
            return false
        }

        override fun isApplicable(elem: PsiElement): Boolean =
            elem is RsCallExpr || elem is RsMethodCall
    };

    companion object

    val smart get() = RsPlainHint.SMART_HINTING.get()
    override val option = Option("SHOW_${this.name}", desc, enabled)
    override val enabled get() = option.get()
}
