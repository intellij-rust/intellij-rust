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
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.selfParameter
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.infer.inferDeclarationType
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

private val RsCallExpr.declaration: RsFunction?
    get() = (expr as? RsPathExpr)?.path?.reference?.resolve() as? RsFunction

private val RsMethodCallExpr.declaration: RsFunction?
    get() = reference.resolve() as? RsFunction

enum class HintType(desc: String, enabled: Boolean) {
    LET_BINDING_HINT("Show local variable type hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val element = elem as? RsLetDecl ?: return emptyList()
            if (element.typeReference != null) return emptyList()
            val ident = element.pat as? RsPatIdent ?: return emptyList()
            val patBinding = ident.patBinding
            val type = patBinding.type
            if (type is TyUnknown) return emptyList()
            return listOf(InlayInfo(": " + type.toString(), patBinding.textRange.endOffset))
        }

        override fun isApplicable(elem: PsiElement): Boolean
            = elem is RsLetDecl
    },
    PARAMETER_HINT("Show argument name hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val (declaration, valueArgumentList) = when (elem) {
                is RsCallExpr -> (elem.declaration to elem.valueArgumentList)
                is RsMethodCallExpr -> (elem.declaration to elem.valueArgumentList)
                else -> null
            } ?: return emptyList()
            val selfParameter = if (declaration?.selfParameter != null && elem is RsCallExpr) {
                1
            } else {
                0
            }
            return declaration?.valueParameters
                ?.mapIndexedNotNull { i, parameter -> transform(i + selfParameter, parameter, valueArgumentList) }
                ?: emptyList()
        }

        override fun isApplicable(elem: PsiElement): Boolean
            = elem is RsCallExpr || elem is RsMethodCallExpr

        fun transform(index: Int, valueParameter: RsValueParameter, valueArgumentList: RsValueArgumentList): InlayInfo? {
            if (index >= valueArgumentList.exprList.size)
                return null
            val pat = valueParameter.pat?.text ?: return null
            return InlayInfo("$pat:", valueArgumentList.exprList[index].textRange.startOffset)
        }
    };

    companion object {
        fun resolve(elem: PsiElement): HintType?
            = HintType.values().find { it.isApplicable(elem) }

        fun resolveToEnabled(elem: PsiElement?): HintType? {
            val resolved = elem?.let { resolve(it) } ?: return null
            return if (resolved.enabled) {
                resolved
            } else {
                null
            }
        }
    }

    abstract fun isApplicable(elem: PsiElement): Boolean
    abstract fun provideHints(elem: PsiElement): List<InlayInfo>
    val option = Option("SHOW_${this.name}", desc, enabled)
    val enabled get() = option.get()
}

class RsInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getSupportedOptions(): List<Option>
        = HintType.values().map { it.option }

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun getHintInfo(element: PsiElement?): HintInfo? = null

    override fun getParameterHints(element: PsiElement?): List<InlayInfo>
        = HintType.resolveToEnabled(element)?.provideHints(element!!) ?: emptyList()

    override fun getInlayPresentation(inlayText: String): String = inlayText
}
