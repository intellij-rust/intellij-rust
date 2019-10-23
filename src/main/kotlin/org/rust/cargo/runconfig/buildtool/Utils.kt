/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rust.cargo.runconfig.buildtool

import com.intellij.build.BuildProgressListener
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.FinishBuildEvent
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.util.Key
import org.rust.cargo.toolchain.CargoCommandLine
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

typealias CargoPatch = (CargoCommandLine) -> CargoCommandLine

val ExecutionEnvironment.cargoPatches: MutableList<CargoPatch>
    get() = putUserDataIfAbsent(CARGO_PATCHES, mutableListOf())

private val CARGO_PATCHES: Key<MutableList<CargoPatch>> = Key.create("CARGO.PATCHES")

private val ExecutionEnvironment.executionListener: ExecutionListener
    get() = project.messageBus.syncPublisher(ExecutionManager.EXECUTION_TOPIC)

fun ExecutionEnvironment.notifyProcessStartScheduled() =
    executionListener.processStartScheduled(executor.id, this)

fun ExecutionEnvironment.notifyProcessStarting() =
    executionListener.processStarting(executor.id, this)

fun ExecutionEnvironment.notifyProcessNotStarted() =
    executionListener.processNotStarted(executor.id, this)

fun ExecutionEnvironment.notifyProcessStarted(handler: ProcessHandler) =
    executionListener.processStarted(executor.id, this, handler)

fun ExecutionEnvironment.notifyProcessTerminating(handler: ProcessHandler) =
    executionListener.processTerminating(executor.id, this, handler)

fun ExecutionEnvironment.notifyProcessTerminated(handler: ProcessHandler, exitCode: Int) =
    executionListener.processTerminated(executor.id, this, handler, exitCode)

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

object EmptyBuildProgressListener : BuildProgressListener {
    override fun onEvent(buildId: Any, event: BuildEvent) = Unit
}

@Suppress("UnstableApiUsage")
class MockBuildProgressListener(buildsCount: Int = 1) : BuildProgressListener {
    private val latch: CountDownLatch = CountDownLatch(buildsCount)
    private val _eventHistory: MutableList<BuildEvent> = mutableListOf()
    val eventHistory: List<BuildEvent> get() = _eventHistory

    override fun onEvent(buildId: Any, event: BuildEvent) {
        _eventHistory.add(event)
        if (event is FinishBuildEvent) {
            latch.countDown()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFinished(timeout: Long = 1, unit: TimeUnit = TimeUnit.MINUTES) {
        latch.await(timeout, unit)
    }
}
