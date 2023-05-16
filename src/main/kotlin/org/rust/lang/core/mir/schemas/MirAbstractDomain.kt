/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

object MirAbstractType
typealias MirAbstractElem = MirProjectionElem<MirAbstractType>

fun PlaceElem.lift(): MirProjectionElem<MirAbstractType> =
    when (this) {
        is MirProjectionElem.Field -> MirProjectionElem.Field(fieldIndex, MirAbstractType)
    }
