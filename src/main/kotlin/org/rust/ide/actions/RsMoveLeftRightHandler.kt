/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.arrayElements

class RsMoveLeftRightHandler : MoveElementLeftRightHandler() {

    override fun getMovableSubElements(element: PsiElement): Array<PsiElement> {
        val subElements = when (element) {
            is RsArrayExpr -> element.arrayElements.orEmpty()
            is RsFormatMacroArgument -> element.formatMacroArgList
            is RsLifetimeParamBounds -> element.lifetimeList
            is RsLogMacroArgument -> element.formatMacroArgList
            is RsMetaItemArgs -> element.metaItemList + element.litExprList
            is RsTupleExpr -> element.exprList
            is RsTupleType -> element.typeReferenceList
            is RsTupleFields -> element.tupleFieldDeclList
            is RsTypeParamBounds -> element.polyboundList
            is RsTypeParameterList -> element.lifetimeParameterList + element.typeParameterList
            is RsUseGroup -> element.useSpeckList
            is RsValueArgumentList -> element.exprList
            is RsValueParameterList -> element.valueParameterList
            is RsVecMacroArgument -> if (element.semicolon == null) element.exprList else emptyList()
            else -> return PsiElement.EMPTY_ARRAY
        }
        return subElements.toTypedArray()
    }
}
