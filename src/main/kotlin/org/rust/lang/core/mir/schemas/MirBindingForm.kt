/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.mir.schemas

sealed class MirBindingForm {
    data class Var(val varBinding: MirVarBindingForm) : MirBindingForm()
    object ReferenceForGuard : MirBindingForm()
    sealed class ImplicitSelf : MirBindingForm() {
        object Immutable : ImplicitSelf()
        object Mutable: ImplicitSelf()
        object ImmutableReference : ImplicitSelf()
        object MutableReference : ImplicitSelf()
        object None : ImplicitSelf()
    }
}
