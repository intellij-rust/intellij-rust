/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints.parameter

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.hints.type.findExpandedByLeaf
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.emptySubstitution
import org.rust.stdext.buildList

@Suppress("UnstableApiUsage")
object RsInlayParameterHints {
    val smartOption: Option = Option("SMART_HINTS", RsBundle.messagePointer("settings.rust.inlay.parameter.hints.only.smart"), true)
    val smart: Boolean get() = smartOption.get()

    fun provideHints(elem: PsiElement): List<InlayInfo> {
        val elementExpanded = findExpandedElement(elem) ?: return emptyList()
        val callInfo = when (elementExpanded) {
            is RsCallExpr -> CallInfo.resolve(elementExpanded)
            is RsMethodCall -> CallInfo.resolve(elementExpanded)
            else -> null
        } ?: return emptyList()

        val valueArgumentList = elem.childOfType<RsValueArgumentList>() ?: return emptyList()
        val hints = buildList {
            if (callInfo.selfParameter != null && elem is RsCallExpr) {
                add(callInfo.selfParameter)
            }
            addAll(callInfo.parameters.map {
                it.pattern ?: (it.typeRef?.substAndGetText(emptySubstitution) ?: "_")
            })
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

    private fun findExpandedElement(element: PsiElement): PsiElement? {
        val valueArgumentList = when (element) {
            is RsCallExpr -> element.valueArgumentList
            is RsMethodCall -> element.valueArgumentList
            else -> return null
        }
        val valueArgumentListExpanded = valueArgumentList.findExpandedByLeaf { it.lparen } ?: return null
        if (valueArgumentListExpanded == valueArgumentList) return element
        if (valueArgumentListExpanded.exprList.size != valueArgumentList.exprList.size) return null
        return valueArgumentListExpanded.parent.takeIf { it.elementType == element.elementType }
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
