/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.unescapedText
import org.rust.lang.core.resolve.ref.RsPathReference

interface RsPathReferenceElement : RsReferenceElement {
    override fun getReference(): RsPathReference?

    override val referenceNameElement: PsiElement

    @JvmDefault
    override val referenceName: String get() = referenceNameElement.unescapedText
}
