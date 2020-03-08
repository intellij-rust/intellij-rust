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
import org.rust.lang.core.resolve.processAttributeProcMacroResolveVariants

class RsAttributeProcMacroReferenceImpl(
    element: RsPath
) : RsReferenceCached<RsPath>(element),
    RsPathReference {

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processAttributeProcMacroResolveVariants(element, it) }

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsFunction && super.isReferenceTo(element)
}
