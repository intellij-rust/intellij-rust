/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsLabel
import org.rust.lang.core.psi.RsLabelDecl
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.resolveLabelReference

class RsLabelReferenceImpl(
    element: RsLabel
) : RsReferenceCached<RsLabel>(element) {

    override val cacheDependency: ResolveCacheDependency get() = ResolveCacheDependency.LOCAL

    override fun resolveInner(): List<RsElement> = resolveLabelReference(element)

    override fun isReferenceTo(element: PsiElement): Boolean =
        element is RsLabelDecl && super.isReferenceTo(element)
}
