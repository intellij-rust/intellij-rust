/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.buildtool

import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import org.rust.cargo.toolchain.CargoCommandLine

typealias CargoPatch = (CargoCommandLine) -> CargoCommandLine

val ExecutionEnvironment.cargoPatches: MutableList<CargoPatch>
    get() = putUserDataIfAbsent(CARGO_PATCHES, mutableListOf())

private val CARGO_PATCHES: Key<MutableList<CargoPatch>> = Key.create("CARGO.PATCHES")

class MockProgressIndicator : EmptyProgressIndicator() {
    private val _textHistory: MutableList<String?> = mutableListOf()
    val textHistory: List<String?> get() = _textHistory

    override fun setText(text: String?) {
        super.setText(text)
        _textHistory += text
    }

    override fun setText2(text: String?) {
        super.setText2(text)
        _textHistory += text
    }
}
