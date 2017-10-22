/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsBinaryExpr
import org.rust.lang.core.psi.RsExpr
import org.rust.lang.core.psi.ext.EqualityOp
import org.rust.lang.core.psi.ext.operatorType

abstract class AssertPostfixTemplateBase(name: String) : StringBasedPostfixTemplate(
    name,
    "$name!(exp);",
    RsTopMostInScopeSelector(RsExpr::isBool)) {

    override fun getTemplateString(element: PsiElement): String =
        if (element is RsBinaryExpr && element.operatorType == EqualityOp.EQ) {
            "${this.presentableName}_eq!(${element.left.text}, ${element.right?.text});\$END$"
        } else {
            "$presentableName!(${element.text});\$END$"
        }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class AssertPostfixTemplate : AssertPostfixTemplateBase("assert")
class DebugAssertPostfixTemplate : AssertPostfixTemplateBase("debug_assert")

class LambdaPostfixTemplate : StringBasedPostfixTemplate(
    "lambda",
    "|| expr",
    RsTopMostInScopeSelector({ true })) {

    override fun getTemplateString(element: PsiElement): String = "|| ${element.text}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class NotPostfixTemplate : StringBasedPostfixTemplate(
    "not",
    "!expr",
    RsTopMostInScopeSelector({ true })) {

    override fun getTemplateString(element: PsiElement): String = "!${element.text}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class MatchPostfixTemplate : StringBasedPostfixTemplate(
    "match",
    "match expr {...}",
    RsTopMostInScopeSelector({ true })
) {
    override fun getTemplateString(element: PsiElement): String = "match ${element.text} {\n\$PAT\$ => {\$END\$}\n}"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement): Unit {
        with(template) {
            addVariable("PAT", TextExpression("_"), true)
        }
    }
}

