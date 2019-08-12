/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage", "FunctionName", "UNUSED_PARAMETER")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.output.BuildOutputInstantReaderImpl
import com.intellij.build.output.BuildOutputParser
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import org.rust.cargo.toolchain.CargoCommandLine
import java.util.concurrent.CompletableFuture

fun BuildOutputInstantReaderImpl(
    buildId: Any,
    parentEventId: Any,
    buildProgressListener: BuildProgressListener,
    parsers: List<BuildOutputParser>
) = BuildOutputInstantReaderImpl(buildId, buildProgressListener, parsers)

fun BuildOutputInstantReaderImpl.closeAndGetFuture(): CompletableFuture<Unit> =
    CompletableFuture.completedFuture(close())

fun BuildProgressListener.onEvent(parentEventId: Any, event: BuildEvent) = onEvent(event)

typealias CargoPatch = (CargoCommandLine) -> CargoCommandLine

val ExecutionEnvironment.cargoPatches: MutableList<CargoPatch>
    get() = putUserDataIfAbsent(CARGO_PATCHES, mutableListOf())

private val CARGO_PATCHES: Key<MutableList<CargoPatch>> = Key.create("CARGO.PATCHES")

object EmptyBuildProgressListener : BuildProgressListener {
    override fun onEvent(event: BuildEvent) = Unit
}

@Suppress("UnstableApiUsage")
class MockBuildProgressListener : BuildProgressListener {
    private val _eventHistory: MutableList<BuildEvent> = mutableListOf()
    val eventHistory: List<BuildEvent> get() = _eventHistory

    override fun onEvent(event: BuildEvent) {
        _eventHistory.add(event)
    }
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
