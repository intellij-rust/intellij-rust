/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

// https://github.com/rust-lang/rust/blob/f7b831ac8a897273f78b9f47165cf8e54066ce4b/compiler/rustc_middle/src/mir/interpret/value.rs#L32
sealed class MirConstValue {
    data class Scalar(val value: MirScalar) : MirConstValue()
    object ZeroSized : MirConstValue()
}
