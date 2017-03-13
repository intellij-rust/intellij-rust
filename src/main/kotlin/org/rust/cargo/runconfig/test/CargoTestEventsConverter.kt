package org.rust.cargo.runconfig.test

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessage
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor

/**
 * Translate Cargo Test output to TeamCity Service Messages which SM Test Runner understands.
 *
 * Unfortunately, as for now Cargo Test only supports `human` message format, which is pretty
 * obscure to handle programmatically. First of all, it is split into some separate sections,
 * which are not easily distinguishable, that's why we do state machines here. Next problem is,
 * that Cargo collects failed tests stdouts and print them yet at the end of test session.
 * Lastly, we do not have exact information when given test started.
 *
 * For SM docs see: [https://confluence.jetbrains.com/display/TCD10/Build+Script+Interaction+with+TeamCity].
 *
 * Some parts based on [TeamCity Rust Plugin](https://github.com/JetBrains/teamcity-rust-plugin).
 */
class CargoTestEventsConverter(consoleProperties: TestConsoleProperties) :
    OutputToGeneralTestEventsConverter("Cargo Test", consoleProperties) {

    private val failedTests: MutableMap<String, FailedTestData> = mutableMapOf()
    private var currentTestName: String? = null

    private var visitor: ServiceMessageVisitor? = null

    private var _state: State = State.Building
    private var state: State
        get() = _state
        set(value) {
            _state.onLeave(this)
            _state = value
            _state.onEnter(this)
        }

    override fun dispose() {
        check(state == State.Summary)
        state.onLeave(this)
        super.dispose()
    }

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        if (this.visitor == null) {
            this.visitor = visitor
            state.onEnter(this)
        }

        val cleanText = text.trim()

        val currentState = state
        val result = state.onMessage(this, cleanText, outputType)
        if (!result && currentState != state) {
            // If we have changed state and haven't processed
            // current message, try to pass it to new state.
            return state.onMessage(this, cleanText, outputType)
        } else {
            return result
        }
    }

    private fun message(sm: String) {
        val message = requireNotNull(ServiceMessage.parse(sm)) { "message processor should build valid SMs" }
        message.visit(requireNotNull(visitor) { "visitor hasn't be initialized" })
    }

    private fun message(smb: ServiceMessageBuilder) = message(smb.toString())

    // Kotlin chokes on inner sealed classes, that's why we use enums here
    private enum class State {
        Building {
            // TODO
            override fun onMessage(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean {
                conv.state = Testing
                return false
            }
        },

        Testing {
            override fun onStdout(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean {
                if (text.trimEnd() == "failures:" || text.startsWith("test results:")) {
                    conv.state = Summary
                    return false
                }

                val matchResult = TEST_PATTERN.find(text) ?: return false

                val testName = matchResult.groupValues[1]
                val result = matchResult.groupValues[2].toLowerCase()
                when (result) {
                    "ok" -> {
                        conv.message(
                            ServiceMessageBuilder.testStarted(testName)
                                .addAttribute("locationHint", CargoTestLocator.getTestFnUrl(testName)))
                        conv.message(ServiceMessageBuilder.testFinished(testName))
                    }

                    "failed" -> conv.failedTests[testName] = FailedTestData()

                    "ignored" -> conv.message(
                        ServiceMessageBuilder.testIgnored(testName)
                            .addAttribute("locationHint", CargoTestLocator.getTestFnUrl(testName)))

                    "bench" -> TODO("running benchmarks is not implemented yet")

                    else -> error("unknown test result")
                }

                return true
            }
        },

        Stdouts {
            override fun onEnter(conv: CargoTestEventsConverter) {
                conv.currentTestName = null
            }

            override fun onLeave(conv: CargoTestEventsConverter) {
                conv.currentTestName = null
            }

            override fun onStdout(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean {
                if (text.trimEnd() == "failures:") {
                    if (conv.currentTestName != null) {
                        conv.state = Summary
                        return false
                    } else {
                        return true
                    }
                }

                val matchResult = TEST_STDOUT_PATTERN.find(text)
                if (matchResult != null) {
                    val testName = matchResult.groupValues[1]
                    check(testName in conv.failedTests)
                    conv.currentTestName = testName
                    return true
                }

                conv.currentTestName?.let { currentTestName ->
                    conv.failedTests[currentTestName]!!.output.appendln(text)
                    return true
                }

                return false
            }
        },

        Summary {
            override fun onStdout(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean {
                if (TEST_STDOUT_PATTERN.containsMatchIn(text)) {
                    conv.state = Stdouts
                }
                return false
            }

            override fun onLeave(conv: CargoTestEventsConverter) {
                conv.failedTests.forEach {
                    val text = it.value.output.trimEnd('\n').toString()
                    val index = text.indexOfAny(arrayListOf(": ", ", "))
                    val error = if (index > 0) text.substring(0, index) else text

                    conv.message(
                        ServiceMessageBuilder.testStarted(it.key)
                            .addAttribute("locationHint", CargoTestLocator.getTestFnUrl(it.key)))

                    conv.message(
                        ServiceMessageBuilder.testFailed(it.key)
                            .addAttribute("message", error)
                            .addAttribute("details", text))

                    conv.message(ServiceMessageBuilder.testFinished(it.key))
                }
            }
        };

        open fun onEnter(conv: CargoTestEventsConverter) {}
        open fun onLeave(conv: CargoTestEventsConverter) {}

        open fun onMessage(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean =
            when (outputType) {
                ProcessOutputTypes.STDOUT -> onStdout(conv, text, outputType)
                ProcessOutputTypes.STDERR -> onStderr(conv, text, outputType)
                else -> false
            }

        open fun onStdout(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean = false
        open fun onStderr(conv: CargoTestEventsConverter, text: String, outputType: Key<*>): Boolean = false
    }

    private data class FailedTestData(
        val output: StringBuilder = StringBuilder()
    )

    companion object {
        private val TEST_PATTERN = Regex("^test\\s([^\\s]+)\\s\\.\\.\\.\\s(ok|failed|ignored|bench)", RegexOption.IGNORE_CASE)
        private val TEST_STDOUT_PATTERN = Regex("^---- ([^\\s]+) stdout ----", RegexOption.IGNORE_CASE)
    }
}
