/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros.errors

import org.rust.lang.core.psi.RsProcMacroKind

sealed class ResolveMacroWithoutPsiError {
    object Unresolved : ResolveMacroWithoutPsiError()
    object Macro2IsNotSupported : ResolveMacroWithoutPsiError()
    object NoProcMacroArtifact : ResolveMacroWithoutPsiError()
    data class UnmatchedProcMacroKind(
        val callKind: RsProcMacroKind,
        val defKind: RsProcMacroKind,
    ) : ResolveMacroWithoutPsiError()
}
