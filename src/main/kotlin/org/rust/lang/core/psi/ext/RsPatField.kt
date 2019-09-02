/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RsPat
import org.rust.lang.core.psi.RsPatBinding
import org.rust.lang.core.psi.RsPatField
import org.rust.lang.core.psi.unescapedText

val RsPatField.kind: RsPatFieldKind
    get() = patBinding?.let { RsPatFieldKind.Shorthand(it, box != null) }
        ?: RsPatFieldKind.Full(patFieldFull!!.referenceNameElement, patFieldFull!!.pat)

// PatField ::= identifier ':' Pat | box? PatBinding
sealed class RsPatFieldKind {
    /**
     * struct S { a: i32 }
     * let S { a : ref b } = ...
     *         ~~~~~~~~~
     */
    data class Full(val ident: PsiElement, val pat: RsPat): RsPatFieldKind()
    /**
     * struct S { a: i32 }
     * let S { ref a } = ...
     *         ~~~~~
     */
    data class Shorthand(val binding: RsPatBinding, val isBox: Boolean): RsPatFieldKind()
}

val RsPatFieldKind.fieldName: String
    get() = when (this) {
        is RsPatFieldKind.Full -> ident.unescapedText
        is RsPatFieldKind.Shorthand -> binding.name!! // can't be null
    }
