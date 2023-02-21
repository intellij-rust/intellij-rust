/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processProcMacroResolveVariants

class RsAttributeProcMacroReferenceImpl(
    element: RsPath
) : RsReferenceCached<RsPath>(element),
    RsPathReference {

    override fun multiResolveUncached(): List<RsElement> =
        collectResolveVariants(element.referenceName) {
            processProcMacroResolveVariants(element, it, isCompletion = false)
        }

    override fun isReferenceToInner(element: PsiElement): Boolean =
        element is RsFunction && super.isReferenceToInner(element)
}
