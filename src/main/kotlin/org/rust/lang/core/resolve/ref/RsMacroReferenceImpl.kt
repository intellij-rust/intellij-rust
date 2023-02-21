/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroBinding
import org.rust.lang.core.psi.RsMacroReference
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processMacroReferenceVariants

class RsMacroReferenceImpl(pattern: RsMacroReference) : RsReferenceCached<RsMacroReference>(pattern) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL

    override fun multiResolveUncached(): List<RsElement>
        = collectResolveVariants(element.referenceName) { processMacroReferenceVariants(element, it) }

    override fun isReferenceToInner(element: PsiElement): Boolean =
        element is RsMacroBinding && super.isReferenceToInner(element)
}
