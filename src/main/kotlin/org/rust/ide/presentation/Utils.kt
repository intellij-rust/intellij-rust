/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.text.StringUtil
import org.rust.ide.icons.addVisibilityIcon
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.type


fun getPresentation(psi: RsElement): ItemPresentation {
    val location = run {
        val mod = psi.containingMod
        "(in ${mod.qualifiedName ?: mod.modName ?: psi.containingFile.name})"
    }

    val name = presentableName(psi)
    return PresentationData(name, location, psi.getIcon(0), null)
}

fun getPresentationForStructure(psi: RsElement): ItemPresentation {
    val presentation = buildString {
        fun appendCommaList(xs: List<String>) {
            append('(')
            append(xs.joinToString(", "))
            append(')')
        }
        append(presentableName(psi))
        when (psi) {
            is RsFunction -> {
                appendCommaList(psi.valueParameters.mapNotNull { it.typeReference?.text })

                val ret = psi.retType?.typeReference
                if (ret != null) append(" -> ${ret.text}")
            }
            is RsConstant -> {
                psi.typeReference?.let { append(": ${it.text}") }
            }
            is RsFieldDecl -> {
                psi.typeReference?.let { append(": ${it.text}") }
            }
            is RsEnumVariant -> {
                val fields = psi.tupleFields
                if (fields != null) {
                    appendCommaList(fields.tupleFieldDeclList.map { it.typeReference.text })
                }
            }
        }
    }
    var icon = psi.getIcon(0)
    if ((psi as? RsVisibilityOwner)?.isPublic == true) {
        icon = icon.addVisibilityIcon(true)
    }
    return PresentationData(presentation, null, icon, null)
}

private fun presentableName(psi: RsElement): String? = when (psi) {
    is RsNamedElement -> psi.name
    is RsImplItem -> {
        val typeName = psi.typeReference?.type?.toString()
        val traitName = psi.traitRef?.path?.referenceName
        when {
            typeName == null -> null
            traitName == null -> typeName
            else -> "$traitName for $typeName"
        }
    }
    else -> null
}

val RsDocAndAttributeOwner.presentableQualifiedName: String?
    get() {
        val qName = (this as? RsQualifiedNamedElement)?.qualifiedName
        if (qName != null) return qName
        if (this is RsMod) return modName
        return name
    }

val String.escaped: String get() = StringUtil.escapeXml(this)

fun breadcrumbName(e: RsElement): String? {
    fun lastComponentWithoutGenerics(path: RsPath) = path.referenceName

    return when (e) {
        is RsMacroDefinition -> e.name?.let { "$it!" }

        is RsModItem, is RsStructOrEnumItemElement, is RsTraitItem, is RsConstant ->
            (e as RsNamedElement).name

        is RsImplItem -> {
            val typeName = run {
                val typeReference = e.typeReference
                (typeReference?.typeElement as? RsBaseType)?.path?.let { lastComponentWithoutGenerics(it) }
                    ?: typeReference?.text
                    ?: return null
            }

            val traitName = e.traitRef?.path?.let { lastComponentWithoutGenerics(it) }
            val start = if (traitName != null) "$traitName for" else "impl"
            "$start $typeName"
        }

        is RsFunction -> e.name?.let { "$it()" }
        else -> null
    }
}
