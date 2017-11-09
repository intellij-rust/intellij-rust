/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*

class PresentationInfo(
    element: RsNamedElement,
    val type: String?,
    val name: String,
    private val declaration: DeclarationInfo
) {
    private val location: String? = element.containingFile?.let { " [${it.name}]" }.orEmpty()

    val projectStructureItemText: String get() = "$name${declaration.suffix}"

    val shortSignatureText = "<b>$name</b>${declaration.suffix.escaped}"
    val signatureText: String = "${declaration.prefix}$shortSignatureText"

    val quickDocumentationText: String
        get() = if (declaration.isAmbiguous && type != null) {
            "<i>$type:</i> "
        } else {
            ""
        } + "$signatureText${valueText.escaped}$location"

    private val valueText: String get() = if (declaration.value.isEmpty()) {
        ""
    } else {
        " ${declaration.value}"
    }
}

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
        is RsLifetimeParameter -> Pair("lifetime", createDeclarationInfo(this, quoteIdentifier, true))
        is RsModItem -> Pair("module", createDeclarationInfo(this, identifier, false))
        is RsMacroDefinition -> Pair("macro", createDeclarationInfo(this, nameIdentifier, false))
        is RsLabelDecl -> {
            val p = parent
            when (p) {
                is RsLoopExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl?.quoteIdentifier, false, listOf(p.loop)))
                is RsForExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl?.quoteIdentifier, false, listOf(p.expr, p.`in`, p.`for`)))
                is RsWhileExpr -> Pair("label", createDeclarationInfo(p, p.labelDecl?.quoteIdentifier, false, listOf(p.condition, p.`while`)))
                else -> Pair("label", createDeclarationInfo(this, quoteIdentifier, true))
            }
        }
        is RsPatBinding -> {
            val patOwner = topLevelPattern.parent
            when (patOwner) {
                is RsLetDecl -> Pair("variable", createDeclarationInfo(patOwner, identifier, false, listOf(patOwner.typeReference)))
                is RsValueParameter -> Pair("value parameter", createDeclarationInfo(patOwner, identifier, true, listOf(patOwner.typeReference)))
                is RsMatchArm -> Pair("match arm binding", createDeclarationInfo(patOwner, identifier, true, listOf(patOwner.patList.lastOrNull())))
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
        else -> Pair(javaClass.simpleName, createDeclarationInfo(this, (this as? RsNameIdentifierOwner)?.nameIdentifier, true))
    }
    return declInfo.second?.let { PresentationInfo(this, declInfo.first, elementName, it) }
}

data class DeclarationInfo(
    val prefix: String = "",
    val suffix: String = "",
    val value: String = "",
    val isAmbiguous: Boolean = false
)

private fun createDeclarationInfo(
    decl: RsElement,
    name: PsiElement?,
    isAmbiguous: Boolean,
    stopAt: List<PsiElement?> = emptyList(),
    valueSeparator: PsiElement? = null
): DeclarationInfo? {
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

    check(signatureStart <= nameStart && nameEnd <= valueStart && valueStart <= end && end <= decl.textLength) {
        "Can't generate signature for `${decl.text}`"
    }

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
