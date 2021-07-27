/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.lang.core.macros.builtin.BuiltinMacroExpander
import org.rust.lang.core.psi.RsMacroBody
import org.rust.lang.core.psi.ext.RsMacroDefinitionBase
import org.rust.stdext.HashCode

sealed class RsMacroData

class RsDeclMacroData(val macroBody: Lazy<RsMacroBody?>): RsMacroData() {
    constructor(def: RsMacroDefinitionBase) : this(lazy(LazyThreadSafetyMode.PUBLICATION) { def.macroBodyStubbed })
}

data class RsProcMacroData(val name: String, val artifact: CargoWorkspaceData.ProcMacroArtifact): RsMacroData()

class RsBuiltinMacroData(val name: String): RsMacroData() {

    fun withHash(): RsMacroDataWithHash<RsBuiltinMacroData> =
        RsMacroDataWithHash(this, HashCode.mix(HashCode.compute(name), BUILTIN_DEF_HASH))

    companion object {
        private val BUILTIN_DEF_HASH = HashCode.compute(BuiltinMacroExpander.EXPANDER_VERSION.toString())
    }
}
