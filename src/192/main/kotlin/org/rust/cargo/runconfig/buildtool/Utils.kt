/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import org.rust.cargo.toolchain.CargoCommandLine

typealias CargoPatch = (CargoCommandLine) -> CargoCommandLine

val ExecutionEnvironment.cargoPatches: MutableList<CargoPatch>
    get() = putUserDataIfAbsent(CARGO_PATCHES, mutableListOf())

private val CARGO_PATCHES: Key<MutableList<CargoPatch>> = Key.create("CARGO.PATCHES")

object EmptyBuildProgressListener : BuildProgressListener {
    override fun onEvent(buildId: Any, event: BuildEvent) = Unit
}

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

@Suppress("UnstableApiUsage")
class MockBuildProgressListener : BuildProgressListener {
    private val _eventHistory: MutableList<BuildEvent> = mutableListOf()
    val eventHistory: List<BuildEvent> get() = _eventHistory

    override fun onEvent(buildId: Any, event: BuildEvent) {
        _eventHistory.add(event)
    }
}
