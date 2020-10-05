/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.ide.utils.CallInfo
import org.rust.ide.utils.isEnabledByCfg
import org.rust.lang.core.psi.RsCallExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.RsLambdaExpr
import org.rust.lang.core.psi.RsMethodCall
import org.rust.lang.core.psi.ext.startOffset
import org.rust.stdext.buildList

@Suppress("UnstableApiUsage")
object RsInlayParameterHints {
    val enabledOption: Option = Option("SHOW_PARAMETER_HINT", { "Show argument name hints" }, true)
    val enabled: Boolean get() = enabledOption.get()

    val smartOption: Option = Option("SMART_HINTS", { "Show only smart hints" }, true)
    val smart: Boolean get() = smartOption.get()

    fun provideHints(elem: PsiElement): List<InlayInfo> {
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
                if (hints.lastOrNull()?.second is RsLambdaExpr) {
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
}
