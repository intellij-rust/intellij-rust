/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.ide.presentation.shortPresentableText
import org.rust.ide.utils.CallInfo
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.startOffset
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type
import org.rust.stdext.buildList

private val RsPat.inlayInfo: List<InlayInfo>
    get() = descendantsOfType<RsPatBinding>().asSequence()
        .filter { patBinding ->
            when {
                // "ignored" bindings like `let _a = 123;`
                patBinding.referenceName.startsWith("_") -> false
                // match foo {
                //     None -> {} // None is enum variant, so we don't want to provide type hint for it
                //     _ -> {}
                // }
                patBinding.reference.resolve() is RsEnumVariant -> false
                else -> true
            }
        }
        .map { it to it.type }
        .filterNot { (_, type) -> type is TyUnknown }
        .map { (patBinding, type) -> InlayInfo(": " + type.shortPresentableText, patBinding.endOffset) }
        .toList()

enum class HintType(desc: String, enabled: Boolean) {
    LET_BINDING_HINT("Show local variable type hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val (expr, pats) = when (elem) {
                is RsLetDecl -> {
                    if (elem.typeReference != null) return emptyList()
                    elem.expr to listOfNotNull(elem.pat)
                }
                is RsCondition -> elem.expr to listOfNotNull(elem.pat)
                is RsMatchExpr -> elem.expr to elem.matchBody?.matchArmList?.flatMap { it.patList }
                else -> return emptyList()
            }

            var finalPats = pats.orEmpty()
            if (smart) {
                val declaration = expr?.declaration
                if (declaration is RsStructItem || declaration is RsEnumVariant) {
                    finalPats = finalPats.filter { it !is RsPatIdent }
                }
            }
            return finalPats.flatMap { it.inlayInfo }
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is RsLetDecl || elem is RsCondition || elem is RsMatchExpr
    },
    PARAMETER_HINT("Show argument name hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val (callInfo, valueArgumentList) = when (elem) {
                is RsCallExpr -> (CallInfo.resolve(elem) to elem.valueArgumentList)
                is RsMethodCall -> (CallInfo.resolve(elem) to elem.valueArgumentList)
                else -> return emptyList()
            }
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
    },
    LAMBDA_PARAMETER_HINT("Show lambda parameter type hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val element = elem as? RsLambdaExpr ?: return emptyList()
            return element.valueParameterList.valueParameterList
                .filter { it.typeReference == null }
                .mapNotNull { it.pat }
                .flatMap { it.inlayInfo }
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is RsLambdaExpr
    },
    FOR_PARAMETER_HINT("Show type hints for for loops parameter", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val element = elem as? RsForExpr ?: return emptyList()
            return element.pat?.inlayInfo ?: emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean = elem is RsForExpr
    };

    companion object {
        val SMART_HINTING = Option("SMART_HINTING", "Show only smart hints", true)
        fun resolve(elem: PsiElement): HintType? =
            HintType.values().find { it.isApplicable(elem) }
    }

    abstract fun isApplicable(elem: PsiElement): Boolean
    abstract fun provideHints(elem: PsiElement): List<InlayInfo>

    val option = Option("SHOW_${this.name}", desc, enabled)
    val enabled get() = option.get()
    val smart get() = SMART_HINTING.get()
}

class RsInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getSupportedOptions(): List<Option> =
        HintType.values().map { it.option } + HintType.SMART_HINTING

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun getHintInfo(element: PsiElement?): HintInfo? = null

    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val resolved = HintType.resolve(element) ?: return emptyList()
        if (!resolved.enabled) return emptyList()
        return resolved.provideHints(element)
    }

    override fun getInlayPresentation(inlayText: String): String = inlayText
}
