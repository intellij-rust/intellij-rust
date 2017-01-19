package org.rust.ide.template.postfix

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.RsElementTypes.EQEQ
import org.rust.lang.core.psi.util.descendantsOfType
import org.rust.lang.core.resolve.lexicalDeclarations
import org.rust.lang.core.types.types.RustEnumType
import org.rust.lang.core.types.resolvedType

open class AssertPostfixTemplateBase(name: String) : StringBasedPostfixTemplate(
    name,
    "$name!(exp);",
    RsTopMostInScopeSelector(RsExpr::isBool)) {

    override fun getTemplateString(element: PsiElement): String =
        if (element is RsBinaryExpr && element.operatorType == EQEQ) {
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

class MatchPostfixTemplate : StringBasedPostfixTemplate(
    "match",
    "match expr {...}",
    RsTopMostInScopeSelector(RsExpr::isEnum)
) {
    override fun getTemplateString(element: PsiElement): String? {
        val enumType = (element as RsExpr).resolvedType as RustEnumType

        val allDeclaration = lexicalDeclarations(element)
            .mapNotNull {
                val path = (it.element as? RsQualifiedNamedElement)?.crateRelativePath ?: return@mapNotNull null
                if (path.segments.lastOrNull()?.name == it.name)
                    return@mapNotNull path
                else
                    return@mapNotNull null
            }
            .toSet()

        val stringBuilder = StringBuilder()
        stringBuilder.append("match ${element.text} {\n")

        val variantList = enumType.item.enumBody.enumVariantList

        val createName: (item: RsEnumVariant) -> String = when {
            variantList.all { it.crateRelativePath in allDeclaration } -> {
                x ->
                x.name ?: ""
            }
            enumType.item.crateRelativePath in allDeclaration -> {
                x ->
                "${enumType.item.name ?: "UnknownEnumName"}::${x.name ?: "UnknownVariantName"}"
            }
            else -> {
                x ->
                x.crateRelativePath.toString()
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

                tupleFields != null -> (0 until tupleFields.descendantsOfType<RsTupleFieldDecl>().size)
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
        val itemsCount = ((element as RsExpr).resolvedType as RustEnumType).item.enumBody.enumVariantList.size

        for (i in 0 until itemsCount) {
            template.addVariable("VAR$i", TextExpression("{}"), true)
        }
    }
}

