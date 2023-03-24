/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.mir.RsBindingModeWrapper

class MirVarBindingForm(
    val bindingMode: RsBindingModeWrapper,
    val tyInfo: MirSpan?,
    val matchPlace: Pair<MirPlace?, MirSpan>?,
    val patternSource: MirSpan,
)
