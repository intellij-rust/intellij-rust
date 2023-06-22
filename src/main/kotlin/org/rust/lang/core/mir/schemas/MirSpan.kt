/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import com.intellij.psi.PsiElement

sealed class MirSpan {
    open val end: MirSpan get() = End(reference)
    open val endPoint: MirSpan get() = EndPoint(reference)
    abstract val reference: PsiElement

    data class Full(override val reference: PsiElement) : MirSpan()

    data class Start(override val reference: PsiElement) : MirSpan()

    data class EndPoint(override val reference: PsiElement) : MirSpan() {
        override val endPoint get() = this
    }

    data class End(override val reference: PsiElement) : MirSpan() {
        override val end: MirSpan get() = this
    }

    object Fake : MirSpan() {
        // end of fake source is still a fake source sound ok
        override val end: MirSpan get() = this
        override val endPoint get() = this
        override val reference: PsiElement get() = error("Fake span have no reference")
    }
}

