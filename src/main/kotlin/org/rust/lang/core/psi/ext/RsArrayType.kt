/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.types.consts.asLong
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.utils.evaluation.evaluate

val RsArrayType.isSlice: Boolean get() = greenStub?.isSlice ?: (expr == null)

val RsArrayType.arraySize: Long? get() = expr?.evaluate(TyInteger.USize)?.asLong()
