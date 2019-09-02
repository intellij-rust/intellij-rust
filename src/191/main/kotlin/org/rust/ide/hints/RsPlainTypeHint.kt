/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.hints

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import org.rust.ide.presentation.shortPresentableText
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rust.lang.core.psi.ext.endOffset
import org.rust.lang.core.psi.ext.patList
import org.rust.lang.core.psi.ext.valueParameters
import org.rust.lang.core.types.declaration
import org.rust.lang.core.types.ty.TyUnknown
import org.rust.lang.core.types.type

enum class RsPlainTypeHint(desc: String, enabled: Boolean) : RsPlainHint {
    LET_BINDING_HINT("Show local variable type hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val (expr, pats) = when (elem) {
                is RsLetDecl -> {
                    if (elem.typeReference != null) return emptyList()
                    elem.expr to listOfNotNull(elem.pat)
                }
                is RsCondition -> elem.expr to elem.patList
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
    LAMBDA_PARAMETER_HINT("Show lambda parameter type hints", true) {
        override fun provideHints(elem: PsiElement): List<InlayInfo> {
            val element = elem as? RsLambdaExpr ?: return emptyList()
            return element.valueParameters
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

    val smart get() = RsPlainHint.SMART_HINTING.get()
    override val enabled get() = option.get()
    override val option = Option("SHOW_${this.name}", desc, enabled)
}

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
