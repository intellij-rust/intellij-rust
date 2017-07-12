/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processMacroCallVariants
import org.rust.lang.core.types.BoundElement

class RsMacroCallReferenceImpl(macroInvocation: RsMacroCall) : RsReferenceBase<RsMacroCall>(macroInvocation) {

    override val RsMacroCall.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.referenceName) { processMacroCallVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processMacroCallVariants(element, it) }
}
