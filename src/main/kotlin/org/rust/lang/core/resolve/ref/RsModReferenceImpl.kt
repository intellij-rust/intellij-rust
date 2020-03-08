/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processModDeclResolveVariants

class RsModReferenceImpl(
    modDecl: RsModDeclItem
) : RsReferenceCached<RsModDeclItem>(modDecl) {

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processModDeclResolveVariants(element, it) }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsFile && super.isReferenceTo(element)
}
