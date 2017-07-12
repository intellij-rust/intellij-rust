/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroInvocation
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.resolve.collectCompletionVariants
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processMacroInvocationVariants
import org.rust.lang.core.types.BoundElement

class RsMacroInvocationReferenceImpl(macroInvocation: RsMacroInvocation) : RsReferenceBase<RsMacroInvocation>(macroInvocation) {

    override val RsMacroInvocation.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun resolveInner(): List<BoundElement<RsCompositeElement>> =
        collectResolveVariants(element.referenceName) { processMacroInvocationVariants(element, it) }

    override fun getVariants(): Array<out Any> =
        collectCompletionVariants { processMacroInvocationVariants(element, it) }
}
