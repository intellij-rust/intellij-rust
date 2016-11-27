package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import org.rust.lang.core.psi.RustExprElement
import org.rust.lang.core.psi.RustPathNamedElement
import org.rust.lang.core.psi.RustTupleFieldDeclElement
import org.rust.lang.core.psi.util.descendentsOfType
import org.rust.lang.core.psi.util.parentOfType
import org.rust.lang.core.resolve.RustResolveEngine
import org.rust.lang.core.resolve.scope.RustResolveScope
import org.rust.lang.core.types.RustEnumType
import org.rust.lang.core.types.util.resolvedType

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

class MatchPostfixTemplate : StringBasedPostfixTemplate(
    "match",
    "match expr {...}",
    RustTopMostInScopeSelector(RustExprElement::isEnum)
) {
    override fun getTemplateString(element: PsiElement): String? {
        val enumType = (element as RustExprElement).resolvedType as RustEnumType

        val allDeclaration = generateSequence(
            element.parentOfType<RustResolveScope>(),
            { it.parentOfType<RustResolveScope>() }
        )
            .flatMap { RustResolveEngine.declarations(it, element) }
            .mapNotNull {
                val path = (it.element as? RustPathNamedElement)?.canonicalCratePath ?: return@mapNotNull null
                if (path.segments.lastOrNull()?.name == it.name)
                    return@mapNotNull path
                else
                    return@mapNotNull null
            }
            .toSet()

        val stringBuilder = StringBuilder()
        stringBuilder.append("match ${element.text} {\n")

        val variantList = enumType.item.enumBody.enumVariantList

        val createName: (item: RustEnumVariantElement) -> String = when {
            variantList.all { it.canonicalCratePath in allDeclaration } -> {
                x -> x.name ?: ""
            }
            enumType.item.canonicalCratePath in allDeclaration -> {
                x -> "${enumType.item.name ?: "UnknownEnumName"}::${x.name ?: "UnknownVariantName"}"
            }
            else -> {
                x -> x.canonicalCratePath.toString()
            }
        }

        for ((i, item) in variantList.withIndex()) {
            val itemName = createName(item)

            val blockFields = item.blockFields
            val tupleFields = item.tupleFields

            val tupleOrStructFields = when {
                blockFields != null -> {
                    blockFields.fieldDeclList
                        .map { it.identifier.text }
                        .joinToString(prefix = "{", separator = ", ", postfix = "}")
                }

                tupleFields != null -> (0 until tupleFields.descendentsOfType<RustTupleFieldDeclElement>().size)
                    .map { "v$it" }
                    .joinToString(prefix = "(", separator = ", ", postfix = ")")

                else -> ""
            }
            stringBuilder.append("$itemName $tupleOrStructFields => \$VAR$i$,\n")
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

