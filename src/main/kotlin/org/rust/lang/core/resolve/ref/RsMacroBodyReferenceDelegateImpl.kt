/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsReferenceElementBase

class RsMacroBodyReferenceDelegateImpl(
    element: RsReferenceElementBase
) : RsReferenceBase<RsReferenceElementBase>(element) {
    override val expandedDelegates: List<RsReference>
        get() = super.expandedDelegates ?: emptyList()

    override fun isReferenceToInner(element: PsiElement): Boolean = false

    override fun multiResolveInner(): List<RsElement> = emptyList()
}
