/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

import org.rust.lang.core.thir.ImplicitSelfKind

sealed class MirBindingForm {
    data class Var(val varBinding: MirVarBindingForm) : MirBindingForm()
    object ReferenceForGuard : MirBindingForm()
    data class ImplicitSelf(val kind: ImplicitSelfKind) : MirBindingForm()
}
