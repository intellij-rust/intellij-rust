/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsMacroCall
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.resolve.collectResolveVariants
import org.rust.lang.core.resolve.processMacroCallVariants

class RsMacroCallReferenceImpl(macroInvocation: RsMacroCall) : RsReferenceCached<RsMacroCall>(macroInvocation) {

    override val RsMacroCall.referenceAnchor: PsiElement
        get() = referenceNameElement

    override fun resolveInner(): List<RsElement> =
        collectResolveVariants(element.referenceName) { processMacroCallVariants(element, it) }

    override fun getVariants(): Array<out Any> = emptyArray() // handled by completion contributor
}
