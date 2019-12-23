/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsPatRest
import org.rust.lang.core.psi.RsPatTupleStruct

val RsPatTupleStruct.patRest: RsPatRest? get() = patList.firstOrNull { it is RsPatRest } as? RsPatRest
