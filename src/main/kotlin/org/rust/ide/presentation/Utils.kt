/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.ide.projectView.PresentationData
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.util.Iconable
import org.rust.ide.colors.RsColor
import org.rust.lang.core.macros.isExpandedFromMacro
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.inference

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
                appendCommaList(psi.valueParameters.mapNotNull { it.typeReference?.getStubOnlyText() })

                val ret = psi.retType?.typeReference
                if (ret != null) append(" -> ${ret.getStubOnlyText()}")
            }
            is RsConstant -> {
                psi.typeReference?.let { append(": ${it.getStubOnlyText()}") }
            }
            is RsNamedFieldDecl -> {
                psi.typeReference?.let { append(": ${it.getStubOnlyText()}") }
            }
            is RsPatBinding -> {
                psi.inference?.let { append(": ${it.getBindingType(psi)}") }
            }
            is RsEnumVariant -> {
                val fields = psi.tupleFields
                if (fields != null) {
                    appendCommaList(fields.tupleFieldDeclList.map { it.typeReference.getStubOnlyText() })
                }
            }
        }
    }
    val icon = psi.getIcon(Iconable.ICON_FLAG_VISIBILITY)
    val textAttributes = when {
        psi is RsDocAndAttributeOwner && !psi.existsAfterExpansionSelf -> RsColor.CFG_DISABLED_CODE.textAttributesKey
        psi.isExpandedFromMacro -> RsColor.GENERATED_ITEM.textAttributesKey
        else -> null
    }

    return PresentationData(presentation, null, icon, textAttributes)
}

private fun presentableName(psi: RsElement): String? {
    return when (psi) {
        is RsNamedElement -> psi.name
        is RsImplItem -> {
            val type = psi.typeReference?.text ?: return null
            val trait = psi.traitRef?.text
            buildString {
                if (trait != null) {
                    append("$trait for ")
                }
                append(type)
                append(typeParameterBounds(psi))
            }
        }
        else -> null
    }
}

private fun typeParameterBounds(impl: RsImplItem): String {
    val allBounds = impl.typeParameters.mapNotNull { param ->
        val name = param.name ?: return@mapNotNull null
        val bounds = param.bounds.mapNotNull inner@{
            val bound = it.bound.traitRef?.path?.referenceName ?: return@inner null
            if (it.hasQ) "?$bound" else bound
        }
        if (bounds.isNotEmpty()) bounds.joinToString(prefix = "$name: ", separator = " + ") else null
    }
    return if (allBounds.isNotEmpty()) allBounds.joinToString(prefix = " where ", separator = ", ") else ""
}

val RsDocAndAttributeOwner.presentableQualifiedName: String?
    get() {
        val qName = (this as? RsQualifiedNamedElement)?.qualifiedName
        if (qName != null) return qName
        if (this is RsMod) return modName
        return name
    }
