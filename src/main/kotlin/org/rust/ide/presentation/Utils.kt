/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import com.intellij.openapi.util.text.StringUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*


val RsDocAndAttributeOwner.presentableQualifiedName: String? get() {
    val qName = (this as? RsQualifiedNamedElement)?.qualifiedName
    if (qName != null) return qName
    if (this is RsMod) return modName
    return name
}

val String.escaped: String get() = StringUtil.escapeXml(this)

fun breadcrumbName(e: RsCompositeElement): String? {
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
