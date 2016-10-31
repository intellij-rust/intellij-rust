package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustBinaryExprElement
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustTupleFieldDeclElement
import org.rust.lang.core.psi.impl.RustPsiImplUtil
import org.rust.lang.core.psi.util.descendentsOfType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.types.RustEnumType
import org.rust.lang.core.types.util.resolvedType

class MatchPostfixTemplate : StringBasedPostfixTemplate(
    "match",
    "match expr {...}",
    RustTopMostInScopeSelector(RustExprElement::isEnum)
) {
    override fun getTemplateString(element: PsiElement): String? {
        val enumType = (element as RustExprElement).resolvedType as RustEnumType

        val allAvailableNames = generateSequence(
            element.parentOfType<RustResolveScope>(),
            { it.parentOfType<RustResolveScope>() }
        )
            .flatMap { RustResolveEngine.declarations(it, element) }
            .mapNotNull { it.element }
            .mapNotNull { RustPsiImplUtil.canonicalCratePath(it) }
            .map { it.toString() }
            .toSet()

        val stringBuilder = StringBuilder()
        stringBuilder.append("match ${element.text} {\n")

        for ((i, item) in enumType.item.enumBody.enumVariantList.withIndex()) {
            val itemCanonicalName = RustPsiImplUtil.canonicalCratePath(item).toString()

            val excessPath = allAvailableNames
                .filter { it.commonPrefixWith(itemCanonicalName).length == it.length }
                .maxBy { it.length }

            val itemShortName = itemCanonicalName.substring(excessPath?.lastIndexOf("::")?.plus(2) ?: 2)

            val tupleOrStructFields = when {
                item.blockFields != null -> item.blockFields!!.fieldDeclList
                    .map { it.identifier.text }
                    .joinToString(prefix = "{", separator = ", ", postfix = "}")

                item.tupleFields != null -> (0 until item.tupleFields!!.descendentsOfType<RustTupleFieldDeclElement>().size)
                    .map { "v$it" }
                    .joinToString(prefix = "(", separator = ", ", postfix = ")")

                else -> ""
            }
            stringBuilder.append("$itemShortName $tupleOrStructFields => \$VAR$i$,\n")
        }
        stringBuilder.append("};\n")

        return stringBuilder.toString()
    }

    override fun getElementToRemove(expr: PsiElement): PsiElement = expr

    override fun setVariables(template: Template, element: PsiElement) {
        super.setVariables(template, element)
        val itemsCount = ((element as RustExprElement).resolvedType as RustEnumType).item.enumBody.enumVariantList.size

        for (i in 0 until itemsCount) {
            template.addVariable("VAR$i", TextExpression("{}"), true)
        }
    }
}
