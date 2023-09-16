/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

class MirVarBindingForm(
    val bindingMode: MirBindingMode,
    val tyInfo: MirSpan?,
    val matchPlace: Pair<MirPlace?, MirSpan>?,
    val patternSource: MirSpan,
)
