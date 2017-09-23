/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.types.BoundElement

interface RsReference : PsiPolyVariantReference {

    override fun getElement(): RsCompositeElement

    override fun resolve(): RsCompositeElement?

    fun advancedResolve(): BoundElement<RsCompositeElement>? = resolve()?.let { BoundElement(it) }

    fun advancedCachedMultiResolve(): List<BoundElement<RsCompositeElement>> = multiResolve().map { BoundElement(it) }

    fun multiResolve(): List<RsCompositeElement>
}


