package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBinaryExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTupleFieldDeclElement
import org.rust.lang.core.psi.util.descendentsOfType
import org.rust.lang.core.types.RustEnumType
import org.rust.lang.core.types.util.resolvedType

class AssertPostfixTemplate : StringBasedPostfixTemplate(
    "assert",
    "assert!(expr);",
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getTemplateString(element: PsiElement): String? =
        "assert!(\$expr$);\$END$"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class AssertEqPostfixTemplate : StringBasedPostfixTemplate(
    "asserteq",
    "assert_eq!(lhs, rhs);",
    RustTopMostInScopeSelector(RustExprElement::isEqExpr)
) {
    override fun getTemplateString(element: PsiElement): String? {
        if (element !is RustBinaryExprElement) return null
        return "assert_eq!(${element.left.text}, ${element.right?.text});\$END$"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class DebugAssertPostfixTemplate : StringBasedPostfixTemplate(
    "assertdeb",
    "debug_assert!(expr);",
    RustTopMostInScopeSelector(RustExprElement::isBool)
) {
    override fun getTemplateString(element: PsiElement): String? =
        "debug_assert!(\$expr$);\$END$"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class DebugAssertEqPostfixTemplate : StringBasedPostfixTemplate(
    "assertdebeq",
    "debug_assert_eq!(lhs, rhs);",
    RustTopMostInScopeSelector(RustExprElement::isEqExpr)
) {
    override fun getTemplateString(element: PsiElement): String? {
        if (element !is RustBinaryExprElement) return null
        return "debug_assert_eq!(${element.left.text}, ${element.right?.text});\$END$"
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class ParenthesesPostfixTemplate : StringBasedPostfixTemplate(
    "par",
    "(expr)",
    RustAllParentsInScopeSelector(RustExprElement::all)
) {
    override fun getTemplateString(element: PsiElement): String? =
        "(\$expr$)\$END$"

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr
}

class MatchPostfixTemplate : StringBasedPostfixTemplate(
    "match",
    "match expr {...}",
    RustTopMostInScopeSelector(RustExprElement::isEnum)
) {
    override fun getTemplateString(element: PsiElement): String? {
        val stringBuilder = StringBuilder()

        val enumType = (element as RustExprElement).resolvedType as RustEnumType

        stringBuilder.append("match ${element.text} {\n")

        for ((i, item) in enumType.item.enumBody.enumVariantList.withIndex()) {
            val pattern = when {
                item.blockFields != null -> item.blockFields!!.fieldDeclList
                    .map { it.identifier.text }
                    .joinToString(prefix = "{", separator = ", ", postfix = "}")

                item.tupleFields != null -> (0 until item.tupleFields!!.descendentsOfType<RustTupleFieldDeclElement>().size)
                    .map { "v$it" }
                    .joinToString(prefix = "(", separator = ", ", postfix = ")")

                else -> ""
            }
            stringBuilder.append("${enumType.item.identifier.text}::${item.name}$pattern => \$VAR$i$,\n")
        }
        stringBuilder.append("};\n")

        return stringBuilder.toString()
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement) {
        super.setVariables(template, element)
        val itemsCount = ((element as RustExprElement).resolvedType as RustEnumType).item.enumBody.enumVariantList.size

        for (i in 0 until itemsCount) {
            template.addVariable("VAR$i", TextExpression("()"), true)
        }
    }
}
