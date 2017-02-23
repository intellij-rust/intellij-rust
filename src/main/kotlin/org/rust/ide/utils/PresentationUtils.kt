package org.rust.ide.utils

import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RsFile
import org.rust.lang.core.psi.impl.mixin.topLevelPattern
import org.rust.lang.core.psi.util.ancestors

class PresentationInfo(
    element: RsNamedElement,
    val type: String?,
    val name: String,
    private val declaration: DeclarationInfo
) {
    private val location: String?

    init {
        location = element.containingFile?.let { " [${it.name}]" }.orEmpty()
    }

    val typeNameText: String get() = if (type == null) { name } else "$type `$name`"

    val projectStructureItemText: String get() = "$name${declaration.suffix}"

    val projectStructureItemTextWithValue: String get() = "$projectStructureItemText${declaration.value}"

    val signatureText: String = "${declaration.prefix}<b>$name</b>${declaration.suffix.escaped}"

    val quickDocumentationText: String
        get() = if (declaration.isAmbiguous && type != null) { "<i>$type:</i> " } else { "" } + "$signatureText${valueText.escaped}$location"

    private val valueText: String get() = if (declaration.value.isEmpty()) { "" } else { " ${declaration.value}" }
}

data class DeclarationInfo(
    val prefix: String = "",
    val suffix: String = "",
    val value: String = "",
    val isAmbiguous: Boolean = false
)

val RsNamedElement.presentationInfo: PresentationInfo? get() {
    val elementName = name ?: return null

    val declInfo = when (this) {
        is RsFunction -> Pair("function", createDeclarationInfo(this, identifier, false, listOf(whereClause, retType, valueParameterList)))
        is RsStructItem -> Pair("struct", createDeclarationInfo(this, identifier, false, if (blockFields != null) listOf(whereClause) else listOf(whereClause, tupleFields)))
        is RsFieldDecl -> Pair("field", createDeclarationInfo(this, identifier, false, listOf(typeReference)))
        is RsEnumItem -> Pair("enum", createDeclarationInfo(this, identifier, false, listOf(whereClause)))
        is RsEnumVariant -> Pair("enum variant", createDeclarationInfo(this, identifier, false, listOf(tupleFields)))
        is RsTraitItem -> Pair("trait", createDeclarationInfo(this, identifier, false, listOf(whereClause)))
        is RsTypeAlias -> Pair("type alias", createDeclarationInfo(this, identifier, false, listOf(typeReference, typeParamBounds, whereClause), eq))
        is RsConstant -> Pair("constant", createDeclarationInfo(this, identifier, false, listOf(expr, typeReference), eq))
        is RsSelfParameter -> Pair("parameter", createDeclarationInfo(this, self, false, listOf(typeReference)))
        is RsTypeParameter -> Pair("type parameter", createDeclarationInfo(this, identifier, true))
        is RsLifetimeDecl -> Pair("lifetime", createDeclarationInfo(this, quoteIdentifier, true))
        is RsModItem -> Pair("module", createDeclarationInfo(this, identifier, false))
        is RsLabelDecl -> {
            val p = parent
            when (p) {
                is RsLoopExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl.quoteIdentifier, false, listOf(p.loop)))
                is RsForExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl.quoteIdentifier, false, listOf(p.expr, p.`in`, p.`for`)))
                is RsWhileExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl.quoteIdentifier, false, listOf(p.condition, p.`while`)))
                else -> Pair("label", createDeclarationInfo(this, quoteIdentifier, true))
            }
        }
        is RsPatBinding -> {
            val patOwner = topLevelPattern?.parent
            when (patOwner) {
                is RsLetDecl -> Pair("variable", createDeclarationInfo(patOwner, identifier, false, listOf(patOwner.typeReference)))
                is RsValueParameter -> Pair("value parameter", createDeclarationInfo(patOwner, identifier, true, listOf(patOwner.typeReference)))
                is RsMatchPat -> Pair("match arm binding", createDeclarationInfo(patOwner, identifier, true, listOf(patOwner.lastChild)))
                is RsCondition -> Pair("condition binding", createDeclarationInfo(patOwner, identifier, true, listOf(patOwner.lastChild)))
                else -> Pair("binding", createDeclarationInfo(this, identifier, true))
            }
        }
        is RsFile -> {
            val mName = modName
            if (isCrateRoot) return PresentationInfo(this, "crate", "crate", DeclarationInfo())
            else if (mName != null) return PresentationInfo(this, "mod", name.substringBeforeLast(".rs"), DeclarationInfo("mod "))
            else Pair("file", DeclarationInfo())
        }
        else -> Pair(javaClass.simpleName, createDeclarationInfo(this, navigationElement, true))
    }
    return declInfo.second?.let { PresentationInfo(this, declInfo.first, elementName, it) }
}

private fun createDeclarationInfo(decl: RsCompositeElement, name: PsiElement?, isAmbiguous: Boolean, stopAt: List<PsiElement?> = emptyList(), valueSeparator: PsiElement? = null): DeclarationInfo? {
    // Break an element declaration into elements. For example:
    //
    // pub const Foo: u32 = 100;
    // ^^^^^^^^^ signature prefix
    //           ^^^ name
    //              ^^^^^ signature suffix
    //                    ^^^^^ value
    //                        ^ end
    if (name == null) return null

    // Remove leading spaces, comments and attributes
    val signatureStart = generateSequence(decl.firstChild) { it.nextSibling }
        .dropWhile { it is PsiWhiteSpace || it is PsiComment || it is RsOuterAttr }
        .firstOrNull()
        ?.startOffsetInParent ?: return null

    val nameStart = name.offsetIn(decl)

    // pick (in order) elements we should stop at
    // if they all fail, drop down to the end of the name element
    val end = stopAt
        .filterNotNull().firstOrNull()
        ?.let { it.startOffsetInParent + it.textLength }
        ?: nameStart + name.textLength

    val valueStart = valueSeparator?.offsetIn(decl) ?: end

    val nameEnd = nameStart + name.textLength

    check(signatureStart <= nameStart && nameEnd <= valueStart
        && valueStart <= end && end <= decl.textLength)

    val prefix = decl.text.substring(signatureStart, nameStart).escaped
    val value = decl.text.substring(valueStart, end)
    val suffix = decl.text.substring(nameEnd, end - value.length)
        .replace("""\s+""".toRegex(), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .replace(" ,", ",")
        .trimEnd()

    return DeclarationInfo(prefix, suffix, value, isAmbiguous)
}

private fun PsiElement.offsetIn(owner: PsiElement): Int =
    ancestors.takeWhile { it != owner }.sumBy { it.startOffsetInParent }

private val String.escaped: String get() = StringUtil.escapeXml(this)
