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
    private val type: String?,
    private val declaration: DeclarationInfo?,
    private val name: String? = element.name
) {
    private val location: String?

    init {
        location = element.containingFile?.let { " [${it.name}]" }.orEmpty()
    }

    val typeNameText: String get() = "$type `$name`"

    val projectStructureItemText: String get() = "$name${declaration?.suffix}"

    val declarationText: String
        get() = if (declaration == null)
            "<b>$name</b>"
        else
            "${declaration.prefix}<b>$name</b>${declaration.suffix}"

    val quickDocumentationText: String
        get() = if (declaration?.isAmbiguous ?: true && type != null) { "<i>$type:</i> " } else { "" } + "$declarationText$location"

}

data class DeclarationInfo(
    val prefix: String,
    val suffix: String,
    val isAmbiguous: Boolean = false
)

val RsNamedElement.presentationInfo: PresentationInfo get() {

    val declInfo = when (this) {
        is RsFunction -> Pair("function", createDeclarationInfo(this, identifier, false, listOf(whereClause, retType, valueParameterList)))
        is RsStructItem -> Pair("struct", createDeclarationInfo(this, identifier, false, if (blockFields != null) listOf(whereClause) else listOf(whereClause, tupleFields)))
        is RsFieldDecl -> Pair("field", createDeclarationInfo(this, identifier, false, listOf(typeReference)))
        is RsEnumItem -> Pair("enum", createDeclarationInfo(this, identifier, false, listOf(whereClause)))
        is RsEnumVariant -> Pair("enum variant", createDeclarationInfo(this, identifier, false, listOf(tupleFields)))
        is RsTraitItem -> Pair("trait", createDeclarationInfo(this, identifier, false, listOf(whereClause)))
        is RsTypeAlias -> Pair("type alias", createDeclarationInfo(this, identifier, false, listOf(typeReference, typeParamBounds, whereClause)))
        is RsConstant -> Pair("constant", createDeclarationInfo(this, identifier, false, listOf(expr, typeReference)))
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
            if (isCrateRoot) return PresentationInfo(this, "crate", DeclarationInfo("", ""), "crate")
            else if (mName != null) return PresentationInfo(this, "mod", DeclarationInfo("mod ", ""), name.substringBeforeLast(".rs"))
            else Pair("file", null)
        }
        else -> Pair(javaClass.simpleName, createDeclarationInfo(this, navigationElement, true))
    }
    return PresentationInfo(this, declInfo.first, declInfo.second)
}

private fun createDeclarationInfo(decl: RsCompositeElement, id: PsiElement?, isAmbiguous: Boolean, stopAt: List<PsiElement?> = emptyList()): DeclarationInfo? {
    if (id == null) return null

    // Remove trailing spaces, comments and attributes
    val signatureStartElement = generateSequence(decl.firstChild) { it.nextSibling }
        .dropWhile { it is PsiWhiteSpace || it is PsiComment || it is RsOuterAttr }
        .firstOrNull()

    val signatureStart = signatureStartElement?.startOffsetInParent ?: return null
    val idStart = id.ancestors.takeWhile { it != decl }.sumBy { it.startOffsetInParent }

    // pick (in order) elements we should stop at
    // if they all fail, drop down to the end of the id element
    val signatureEnd = stopAt
        .filterNotNull().firstOrNull()
        ?.let { it.startOffsetInParent + it.textLength }
        ?: idStart + id.textLength

    val idEnd = idStart + id.textLength
    check(signatureStart <= idStart && idStart <= idEnd &&
        idEnd <= signatureEnd && signatureEnd <= decl.textLength)

    val beforeIdent = decl.text.substring(signatureStart, idStart).escaped
    val afterIdent = decl.text.substring(idEnd, signatureEnd)
        .replace("""\s+""".toRegex(), " ")
        .replace("( ", "(")
        .replace(" )", ")")
        .replace(" ,", ",")
        .trimEnd()
        .escaped

    return DeclarationInfo(beforeIdent, afterIdent, isAmbiguous)
}

private val String.escaped: String get() = StringUtil.escapeXml(this)
