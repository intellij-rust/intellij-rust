/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.ref

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import org.rust.cargo.util.AutoInjectedCrates
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsQualifiedName
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.lang.core.types.ty.TyPrimitive
import org.rust.lang.doc.psi.RsDocLinkDestination
import java.net.URI

class RsDocLinkDestinationReferenceImpl(
    element: RsDocLinkDestination
) : PsiReferenceBase<RsDocLinkDestination>(element, TextRange(0, element.textLength), false) {
    override fun resolve(): PsiElement? {
        val path = when (val owner = element.owner) {
            is RsQualifiedNamedElement -> RsQualifiedName.from(owner)?.toUrlPath()
            // generating documentation for primitive types via the corresponding module
            is RsPath -> if (TyPrimitive.fromPath(owner) != null) "${AutoInjectedCrates.STD}/" else return null
            else -> return null
        }

        val destination = element.text

        val targetUri = if (path != null) {
            try {
                val base = URI.create(path)
                if (destination.startsWith("#")) {
                    URI.create(base.path + destination).normalize()
                } else {
                    base.resolve(destination).normalize()
                }.toString()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        } ?: return null
        return RsQualifiedName.from(targetUri)?.findPsiElement(element.manager, element.ancestorOrSelf<RsElement>()!!)
    }
}
