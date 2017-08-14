/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsRefLikeType
import org.rust.lang.core.types.ty.Mutability

val RsRefLikeType.mutability: Mutability get() = Mutability.valueOf(stub?.isMut ?: (mut != null))
val RsRefLikeType.isRef: Boolean get() = stub?.isRef ?: (and != null)
val RsRefLikeType.isPointer: Boolean get() = stub?.isPointer ?: (mul != null)
