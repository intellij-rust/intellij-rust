/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsArrayType
import org.rust.lang.core.stubs.RsArrayTypeStub
import org.rust.lang.core.types.consts.asInteger
import org.rust.lang.core.types.ty.TyInteger
import org.rust.lang.utils.evaluation.evaluate
import java.math.BigInteger

val RsArrayType.isSlice: Boolean get() = (greenStub as? RsArrayTypeStub)?.isSlice ?: (expr == null)

val RsArrayType.arraySize: BigInteger? get() = expr?.evaluate(TyInteger.USize)?.value?.asInteger()
