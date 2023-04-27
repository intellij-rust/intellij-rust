/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirConstValue {
    data class Scalar(val value: MirScalar) : MirConstValue()
    object ZeroSized : MirConstValue()
}
