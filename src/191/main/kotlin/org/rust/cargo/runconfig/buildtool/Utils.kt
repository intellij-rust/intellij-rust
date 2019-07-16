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
