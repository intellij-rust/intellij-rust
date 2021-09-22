/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig

import com.intellij.execution.ExecutionListener
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ConcurrencyUtil
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@Throws(InterruptedException::class)
fun CountDownLatch.waitFinished(timeoutMs: Long): Boolean {
    for (i in 1..timeoutMs / ConcurrencyUtil.DEFAULT_TIMEOUT_MS) {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
        if (await(ConcurrencyUtil.DEFAULT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) return true
    }
    return false
}

/**
 * Capturing adapter that removes ANSI escape codes from the output
 */
class AnsiAwareCapturingProcessAdapter : ProcessAdapter(), AnsiEscapeDecoder.ColoredTextAcceptor {
    val output = ProcessOutput()

    private val decoder = object : AnsiEscapeDecoder() {
        override fun getCurrentOutputAttributes(outputType: Key<*>) = outputType
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) =
        decoder.escapeText(event.text, outputType, this)

    private fun addToOutput(text: String, outputType: Key<*>) {
        if (outputType === ProcessOutputTypes.STDERR) {
            output.appendStderr(text)
        } else {
            output.appendStdout(text)
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        output.exitCode = event.exitCode
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) =
        addToOutput(text, attributes)
}

class TestExecutionListener(
    private val parentDisposable: Disposable,
    private val configuration: RunConfiguration
) : ExecutionListener {

    private val latch = CountDownLatch(1)
    private val processListener = AnsiAwareCapturingProcessAdapter()

    private var isProcessStarted = false
    private var notStartedCause: Throwable? = null
    private var descriptor: RunContentDescriptor? = null

    override fun processNotStarted(executorId: String, env: ExecutionEnvironment, cause: Throwable?) {
        checkAndExecute(env) {
            notStartedCause = cause
            latch.countDown()
        }
    }

    override fun processStarting(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        checkAndExecute(env) {
            isProcessStarted = true
            handler.addProcessListener(processListener)
            Disposer.register(parentDisposable) {
                ExecutionManagerImpl.stopProcess(handler)
            }
        }
    }

    override fun processTerminated(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler, exitCode: Int) {
        checkAndExecute(env) {
            descriptor = env.contentToReuse?.also {
                Disposer.register(parentDisposable, it)
            }
            latch.countDown()
        }
    }

    private fun checkAndExecute(env: ExecutionEnvironment, action: () -> Unit) {
        if (env.runProfile == configuration) {
            action()
        }
    }

    @Throws(InterruptedException::class)
    fun waitFinished(timeoutMs: Long = 5000): TestExecutionResult? {
        if (!latch.waitFinished(timeoutMs)) return null
        val output = if (isProcessStarted) processListener.output else null
        return TestExecutionResult(output, descriptor, notStartedCause)
    }
}

data class TestExecutionResult(
    val output: ProcessOutput?,
    val descriptor: RunContentDescriptor?,
    val notStartedCause: Throwable?
)
