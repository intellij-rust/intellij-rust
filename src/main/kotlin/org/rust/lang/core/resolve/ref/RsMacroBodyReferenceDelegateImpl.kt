/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiElement
import org.rust.lang.core.macros.findExpansionElements
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsReferenceElementBase
import org.rust.lang.core.psi.ext.ancestors
import org.rust.openapiext.Testmark

class RsMacroBodyReferenceDelegateImpl(
    element: RsReferenceElementBase
) : RsReferenceBase<RsReferenceElementBase>(element) {
    override val RsReferenceElementBase.referenceAnchor: PsiElement?
        get() = element.referenceNameElement

    private val delegates: List<RsReference>
        get() {
            Testmarks.touched.hit()
            return element.findExpansionElements()?.mapNotNull { delegated ->
                delegated.ancestors
                    .mapNotNull { it.reference }
                    .firstOrNull() as? RsReference
            }.orEmpty()
        }

    override fun isReferenceTo(element: PsiElement): Boolean {
        return delegates.any { it.isReferenceTo(element) }
    }

    override fun multiResolve(): List<RsElement> =
        delegates.flatMap { it.multiResolve() }.distinct()

    object Testmarks {
        val touched = Testmark("touched")
    }
}
