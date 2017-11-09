/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.PsiPolyVariantReference
import org.rust.lang.core.psi.ext.RsElement

interface RsReference : PsiPolyVariantReference {

    override fun getElement(): RsElement

    override fun resolve(): RsElement?

    fun multiResolve(): List<RsElement>
}


