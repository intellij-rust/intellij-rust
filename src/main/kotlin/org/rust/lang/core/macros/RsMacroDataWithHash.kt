/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.resolve2.DeclMacro2DefInfo
import org.rust.lang.core.resolve2.DeclMacroDefInfo
import org.rust.lang.core.resolve2.MacroDefInfo
import org.rust.lang.core.resolve2.ProcMacroDefInfo
import org.rust.stdext.HashCode

class RsMacroDataWithHash<T : RsMacroData>(
    val data: T,
    val bodyHash: HashCode?
) {
    fun mixHash(call: RsMacroCallDataWithHash): HashCode? {
        @Suppress("USELESS_CAST") // False-positive
        val callHash = when (data as RsMacroData) {
            is RsDeclMacroData -> call.bodyHash
            is RsProcMacroData -> call.hashWithEnv()
            is RsBuiltinMacroData -> call.bodyHash
        }
        return HashCode.mix(bodyHash ?: return null, callHash ?: return null)
    }

    companion object {
        fun fromPsi(def: RsNamedElement): RsMacroDataWithHash<out RsMacroData>? {
            return when {
                def is RsMacro -> if (def.hasRustcBuiltinMacro) {
                    RsBuiltinMacroData(def.name ?: return null).withHash()
                } else {
                    RsMacroDataWithHash(RsDeclMacroData(def), def.bodyHash)
                }
                def is RsFunction && def.isProcMacroDef -> {
                    val name = def.procMacroName ?: return null
                    val procMacro = def.containingCrate?.procMacroArtifact ?: return null
                    val hash = HashCode.mix(procMacro.hash, HashCode.compute(name))
                    RsMacroDataWithHash(RsProcMacroData(name, procMacro), hash)
                }
                else -> null
            }
        }

        fun fromDefInfo(def: MacroDefInfo): RsMacroDataWithHash<out RsMacroData>? {
            return when (def) {
                is DeclMacroDefInfo -> if (def.hasRustcBuiltinMacro) {
                    RsBuiltinMacroData(def.path.name).withHash()
                } else {
                    RsMacroDataWithHash(RsDeclMacroData(def.body), def.bodyHash)
                }
                is DeclMacro2DefInfo -> null  // TODO
                is ProcMacroDefInfo -> {
                    val name = def.path.name
                    val procMacroArtifact = def.procMacroArtifact ?: return null
                    val hash = HashCode.mix(procMacroArtifact.hash, HashCode.compute(name))
                    RsMacroDataWithHash(RsProcMacroData(name, procMacroArtifact), hash)
                }
            }
        }
    }
}
