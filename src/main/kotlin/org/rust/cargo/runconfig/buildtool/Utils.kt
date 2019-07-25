/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.util.Key
import org.rust.cargo.toolchain.CargoCommandLine

typealias CargoPatch = (CargoCommandLine) -> CargoCommandLine

val ExecutionEnvironment.cargoPatches: MutableList<CargoPatch>
    get() = putUserDataIfAbsent(CARGO_PATCHES, mutableListOf())

private val CARGO_PATCHES: Key<MutableList<CargoPatch>> = Key.create("CARGO.PATCHES")
