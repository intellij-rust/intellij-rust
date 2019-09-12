/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent

object EmptyBuildProgressListener : BuildProgressListener {
    override fun onEvent(buildId: Any, event: BuildEvent) = Unit
}

@Suppress("UnstableApiUsage")
class MockBuildProgressListener : BuildProgressListener {
    private val _eventHistory: MutableList<BuildEvent> = mutableListOf()
    val eventHistory: List<BuildEvent> get() = _eventHistory

    override fun onEvent(buildId: Any, event: BuildEvent) {
        _eventHistory.add(event)
    }
}
