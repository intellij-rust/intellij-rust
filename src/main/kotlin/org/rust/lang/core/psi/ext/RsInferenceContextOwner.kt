/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.psi.RsConstant
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsVariantDiscriminant

/**
 * PSI element that implements this interface holds type inference context that can be retrieved for each child element
 * by [org.rust.lang.core.types.inference]
 *
 * @see org.rust.lang.core.types.infer.RsInferenceContext.infer
 */
interface RsInferenceContextOwner : RsElement

val RsInferenceContextOwner.body: RsElement?
    get() = when (this) {
        is RsArrayType -> expr
        is RsConstant -> expr
        is RsFunction -> block
        is RsVariantDiscriminant -> expr
        else -> null
    }
