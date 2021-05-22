/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.ext.RsPossibleMacroCall
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.macroBody

class RsMacroCallData(
    val macroBody: MacroCallBody?,
    val packageEnv: Map<String, String>
) {

    companion object {
        fun fromPsi(call: RsPossibleMacroCall): RsMacroCallData = RsMacroCallData(
            call.macroBody,
            call.containingCargoPackage?.env.orEmpty()
        )
    }
}
