/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.schemas.MirProjectionElem.*

object MirAbstractType
typealias MirAbstractElem = MirProjectionElem<MirAbstractType>

fun PlaceElem.lift(): MirProjectionElem<MirAbstractType> =
    when (this) {
        is Deref -> Deref
        is Field -> Field(fieldIndex, MirAbstractType)
        is Index -> Index(index)
        is ConstantIndex -> ConstantIndex(offset, minLength, fromEnd)
        is Downcast -> Downcast(name, variantIndex)
    }
