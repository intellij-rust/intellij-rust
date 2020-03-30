/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateWithExpressionSelector
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve.knownItems
import org.rust.lang.core.types.TraitRef
import org.rust.lang.core.types.implLookup
import org.rust.lang.core.types.ty.TyReference
import org.rust.lang.core.types.ty.TyStr
import org.rust.lang.core.types.ty.TyUnit
import org.rust.lang.core.types.type

class PrintlnPostfixTemplate(provider: RsPostfixTemplateProvider, private val macroName: String = "println") :
    PostfixTemplateWithExpressionSelector(
        null,
        macroName,
        "$macroName!(\"{:?}\", expr);",
        RsTopMostInScopeSelector { it.isNotIgnored && (it.isDebug || it.isDisplay) },
        provider
    ) {

    private enum class Fmt(val text: String) {
        None(""),
        Display("{}"),
        Debug("{:?}");

        companion object {
            fun fromExpr(expr: RsExpr): Fmt {
                val kind = (expr as? RsLitExpr)?.kind
                return when {
                    kind is RsLiteralKind.String && !kind.isByte -> None
                    expr.isDisplay -> Display
                    else -> Debug
                }
            }
        }
    }

    private class MacroCreator(private val editor: Editor, private val psiFactory: RsPsiFactory,
                               macroName: String, private val fmt: Fmt) {
        private val macroStart = "$macroName!("

        fun createMacro(expressionText: String, addTrailingSemicolon: Boolean = true): RsExpr {
            val macroExpression = if (fmt == Fmt.None) {
                psiFactory.createExpression("$macroStart$expressionText)")
            } else {
                psiFactory.createExpression("$macroStart\"${fmt.text}\", $expressionText)")
            }
            if (addTrailingSemicolon) {
                macroExpression.add(psiFactory.createSemicolon())
            }
            return macroExpression
        }

        fun replaceWithMacro(expression: PsiElement): PsiElement {
            return expression.replace(createMacro(expression.text, expression.parent is RsBlock))
        }
    }

    override fun expandForChooseExpression(expression: PsiElement, editor: Editor) {
        if (expression !is RsExpr) return

        val psiFactory = RsPsiFactory(expression.project)
        val macroCreator = MacroCreator(editor, psiFactory, macroName, Fmt.fromExpr(expression))

        when (val parent = expression.parent) {
            is RsLetDecl -> {
                val expressionText = parent.pat?.text ?: return
                addMacroNextToElement(macroCreator.createMacro(expressionText), parent, psiFactory, editor)
                if (parent.semicolon == null) {
                    parent.add(psiFactory.createSemicolon())
                }
            }
            is RsMatchArm -> {
                val matchBody = parent.ancestorStrict<RsMatchBody>() ?: return
                if (matchBody.containsUnitArm) {
                    val newElement = macroCreator.replaceWithMacro(expression)
                    val caretAnchor = if (parent.comma == null) {
                        parent.addAfter(psiFactory.createComma(), newElement)
                    } else {
                        newElement
                    }
                    editor.caretModel.moveToOffset(caretAnchor.endOffset)
                } else {
                    surroundWithBlockExpression(macroCreator.createMacro(expression.text), expression, psiFactory, editor)
                }
            }
            is RsBinaryExpr -> addMacroNextToElement(macroCreator.createMacro(expression.text), parent, psiFactory, editor)
            else -> {
                val newElement = macroCreator.replaceWithMacro(expression)
                editor.caretModel.moveToOffset(newElement.endOffset)
            }
        }
    }

    private fun addMacroNextToElement(macro: RsExpr, element: PsiElement, psiFactory: RsPsiFactory, editor: Editor) {
        val childToContainer = element.closestContainerAncestor ?: return
        val container = childToContainer.second
        if (container is RsMatchArm) {
            surroundWithBlockExpression(macro, childToContainer.first, psiFactory, editor)
        } else {
            val newElement = container.addAfter(macro, childToContainer.first.getNextNonCommentSibling()?.prevSibling)
            editor.caretModel.moveToOffset(newElement.endOffset)
            newElement.add(psiFactory.createNewline())
        }
    }

    private fun surroundWithBlockExpression(macro: RsExpr, originalExpression: PsiElement, psiFactory: RsPsiFactory, editor: Editor) {
        val newBlock = psiFactory.createBlockExpr("${macro.text}\n${originalExpression.text}")
        val newElement = originalExpression.replace(newBlock) as RsBlockExpr
        editor.caretModel.moveToOffset(newElement.block.children[0].endOffset)
    }
}

private val PsiElement.closestContainerAncestor: Pair<PsiElement, PsiElement>?
    get() = ancestorPairs.dropWhile { it.second !is RsBlock && it.second !is RsMatchArm }.firstOrNull()

private val RsMatchBody.containsUnitArm: Boolean
    get() = matchArmList.any { it.expr?.type is TyUnit }

private val RsExpr.isNotIgnored: Boolean
    get() {
        val parent = parent ?: return false
        return when (parent) {
            is RsLetDecl -> parent.pat !is RsPatWild
            is RsMatchArm, is RsStmt, is RsBlock, is RsBinaryExpr -> true
            else -> false
        }
    }

private val RsExpr.isDebug: Boolean
    get() = knownItems.Debug?.withSubst()?.let { implLookup.canSelectWithDeref(TraitRef(type, it)) } ?: false

private val RsExpr.isDisplay: Boolean
    get() = knownItems.Display?.withSubst()?.let { implLookup.canSelectWithDeref(TraitRef(type, it)) } ?: false

