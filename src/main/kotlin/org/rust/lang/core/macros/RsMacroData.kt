/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.core.psi.ext.macroBodyStubbed

sealed class RsMacroData

class RsDeclMacroData(val macroBody: Lazy<RsMacroBody?>): RsMacroData() {
    constructor(def: RsMacro) : this(lazy(LazyThreadSafetyMode.PUBLICATION) { def.macroBodyStubbed })
}

data class RsProcMacroData(val name: String, val artifact: CargoWorkspaceData.ProcMacroArtifact): RsMacroData()
