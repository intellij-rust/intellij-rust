/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.unescapedText
import org.rust.lang.core.resolve.ref.RsReference

/**
 * Provides basic methods for reference implementation ([org.rust.lang.core.resolve.ref.RsReferenceBase]).
 * This interface should not be used in any analysis.
 */
interface RsReferenceElementBase : RsElement {
    val referenceNameElement: PsiElement?

    @JvmDefault
    val referenceName: String? get() = referenceNameElement?.unescapedText
}

/**
 * Marks an element that optionally can have a reference.
 */
interface RsReferenceElement : RsReferenceElementBase {
    override fun getReference(): RsReference?
}

/**
 * Marks an element that has a reference.
 */
interface RsMandatoryReferenceElement : RsReferenceElement {

    override val referenceNameElement: PsiElement

    @JvmDefault
    override val referenceName: String get() = referenceNameElement.unescapedText

    override fun getReference(): RsReference
}
