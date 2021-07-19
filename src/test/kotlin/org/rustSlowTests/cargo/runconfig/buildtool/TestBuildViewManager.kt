/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.BuildViewManager
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.openapi.project.Project
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestBuildViewManager(project: Project, buildsCount: Int = 1) : BuildViewManager(project) {
    private val latch: CountDownLatch = CountDownLatch(buildsCount)
    private val _eventHistory: MutableList<BuildEvent> = mutableListOf()
    val eventHistory: List<BuildEvent> get() = _eventHistory

    override fun onEvent(buildId: Any, event: BuildEvent) {
        super.onEvent(buildId, event)
        _eventHistory.add(event)
        if (event is FinishBuildEvent) {
            latch.countDown()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFinished(timeoutMs: Long = 5000) {
        for (i in 1..timeoutMs / ConcurrencyUtil.DEFAULT_TIMEOUT_MS) {
            UIUtil.dispatchAllInvocationEvents()
            if (latch.await(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) break
        }
    }
}
