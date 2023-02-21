/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsNamedFieldDecl
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatField
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.isConstantLike
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processPatBindingResolveVariants

class RsPatBindingReferenceImpl(
    element: RsPatBinding
) : RsReferenceCached<RsPatBinding>(element) {

    override fun multiResolveUncached(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processPatBindingResolveVariants(element, false, it) }

    override fun isReferenceToInner(element: PsiElement): Boolean {
        if (element !is RsElement || !element.isConstantLike && element !is RsNamedFieldDecl) return false
        val target = resolveInner()
        return element.manager.areElementsEquivalent(target, element)
    }

    override fun handleElementRename(newName: String): PsiElement {
        if (element.parent !is RsPatField) return super.handleElementRename(newName)
        val newPatField = RsPsiFactory(element.project)
            .createPatFieldFull(newName, element.text)
        return element.replace(newPatField)
    }
}
