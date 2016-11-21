package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*

open class AssertPostfixTemplateBase(name: String) : StringBasedPostfixTemplate(
    name,
    "$name!(exp);",
    RustTopMostInScopeSelector(RustExprElement::isBool)) {

    override fun getTemplateString(element: PsiElement): String =
        if (element is RustBinaryExprElement && element.operatorType == RustTokenElementTypes.EQEQ) {
            "${this.presentableName}_eq!(${element.left.text}, ${element.right?.text});\$END$"
        } else {
            "$presentableName!(${element.text});\$END$"
        }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class AssertPostfixTemplate : AssertPostfixTemplateBase("assert")
class DebugAssertPostfixTemplate : AssertPostfixTemplateBase("debug_assert")
