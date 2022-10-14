/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.types.ty

import org.rust.lang.core.psi.RsInferType
import org.rust.lang.core.types.HAS_TY_PLACEHOLDER_MASK

/**
 * The type appears on `_` path parameter (like `Vec<_>`), then it have to be replaced with [TyInfer.TyVar]
 */
data class TyPlaceholder(
    val origin: RsInferType
): Ty(HAS_TY_PLACEHOLDER_MASK)
