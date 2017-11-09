/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processModDeclResolveVariants

class RsModReferenceImpl(
    modDecl: RsModDeclItem
) : RsReferenceCached<RsModDeclItem>(modDecl),
    RsReference {

    override val RsModDeclItem.referenceAnchor: PsiElement get() = identifier

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processModDeclResolveVariants(element, it) }

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processModDeclResolveVariants(element, it) }
}
