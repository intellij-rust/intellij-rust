/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.google.gson.*
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.util.Key
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor
import org.rust.cargo.runconfig.test.CargoTestLocator.getTestFnUrl
import org.rust.cargo.runconfig.test.CargoTestLocator.getTestModUrl
import org.rust.stdext.removeLast
import java.io.File

private typealias NodeId = String

class CargoTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val parser: JsonParser = JsonParser()

    private val suitesStack: MutableList<NodeId> = mutableListOf()
    private val target: NodeId get() = suitesStack.firstOrNull() ?: ROOT_SUITE
    private val currentSuite: NodeId get() = suitesStack.lastOrNull() ?: ROOT_SUITE

    private val suitesToNotFinishedChildren: MutableMap<NodeId, MutableSet<NodeId>> = hashMapOf()
    private val testsStartTimes: MutableMap<NodeId, Long> = hashMapOf()
    private val pendingFinishedSuites: MutableSet<NodeId> = linkedSetOf()

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        if (handleExecutableName(text)) return true

        val jsonElement: JsonElement
        try {
            jsonElement = parser.parse(text)
            if (!jsonElement.isJsonObject) return true
        } catch (es: JsonSyntaxException) {
            return true
        }

        if (handleTestMessage(jsonElement, outputType, visitor)) return true
        if (handleSuiteMessage(jsonElement, outputType, visitor)) return true

        return true
    }

    /** @return true if message successfully processed. */
    private fun handleExecutableName(text: String): Boolean {
        if (suitesStack.isNotEmpty() || !TARGET_PATH_PART_REGEX.containsMatchIn(text)) return false
        val targetName = text
            .substringAfterLast(File.separatorChar)
            .substringBefore(' ')
            .substringBeforeLast('-')
        suitesStack.add(targetName)
        return true
    }

    private fun handleTestMessage(
        jsonElement: JsonElement,
        outputType: Key<*>,
        visitor: ServiceMessageVisitor
    ): Boolean {
        val testMessage = LibtestTestMessage.fromJson(jsonElement.asJsonObject)
            ?.let { it.copy(name = "$target::${it.name}") }
            ?: return false
        val messages = createServiceMessagesFor(testMessage) ?: return false
        for (message in messages) {
            super.processServiceMessages(message.toString(), outputType, visitor)
        }
        return true
    }

    private fun handleSuiteMessage(
        jsonElement: JsonElement,
        outputType: Key<*>,
        visitor: ServiceMessageVisitor
    ): Boolean {
        val suiteMessage = LibtestSuiteMessage.fromJson(jsonElement.asJsonObject) ?: return false
        val messages = createServiceMessagesFor(suiteMessage) ?: return false
        for (message in messages) {
            super.processServiceMessages(message.toString(), outputType, visitor)
        }
        return true
    }

    private fun createServiceMessagesFor(testMessage: LibtestTestMessage): List<ServiceMessageBuilder>? {
        val messages = mutableListOf<ServiceMessageBuilder>()
        when (testMessage.event) {
            "started" -> {
                recordTestStartTime(testMessage.name)
                recursivelyInitContainingSuite(testMessage.name, messages)
                recordSuiteChildStarted(testMessage.name)
                messages.add(createTestStartedMessage(testMessage.name))
            }
            "ok" -> {
                val duration = getTestDuration(testMessage.name)
                messages.add(createTestFinishedMessage(testMessage.name, duration))
                recordSuiteChildFinished(testMessage.name)
                processFinishedSuites(messages)
            }
            "failed" -> {
                val duration = getTestDuration(testMessage.name)
                val (stdout, failedMessage) = parseFailedTestOutput(testMessage.stdout ?: "")
                if (stdout.isNotEmpty()) messages.add(createTestStdOutMessage(testMessage.name, stdout + '\n'))
                messages.add(createTestFailedMessage(testMessage.name, failedMessage))
                messages.add(createTestFinishedMessage(testMessage.name, duration))
                recordSuiteChildFinished(testMessage.name)
                processFinishedSuites(messages)
            }
            "ignored" -> {
                messages.add(createTestIgnoredMessage(testMessage.name))
            }
            else -> return null
        }
        return messages
    }

    private fun createServiceMessagesFor(suiteMessage: LibtestSuiteMessage): List<ServiceMessageBuilder>? {
        val messages = mutableListOf<ServiceMessageBuilder>()
        when (suiteMessage.event) {
            "started" -> {
                if (suiteMessage.test_count.toInt() == 0) return emptyList()
                messages.add(createTestSuiteStartedMessage(target))
                messages.add(createTestCountMessage(suiteMessage.test_count))
            }
            "ok", "failed" -> {
                pendingFinishedSuites.mapTo(messages) { createTestSuiteFinishedMessage(it) }
                pendingFinishedSuites.clear()
                suitesStack.reversed().mapTo(messages) { createTestSuiteFinishedMessage(it) }
                suitesStack.clear()
                testsStartTimes.clear()
            }
            else -> return null
        }
        return messages
    }

    private data class FailedTestOutput(val stdout: String, val failedMessage: String)

    private fun recordTestStartTime(test: NodeId) {
        testsStartTimes[test] = System.currentTimeMillis()
    }

    private fun recordSuiteChildStarted(node: NodeId) {
        suitesToNotFinishedChildren.getOrPut(node.parent) { hashSetOf() }.add(node)
    }

    private fun recordSuiteChildFinished(node: NodeId) {
        suitesToNotFinishedChildren[node.parent]?.remove(node)
    }

    // Yes, we can't measure the test duration in this way, it should be implemented on the libtest side,
    // but for now, this is an acceptable solution.
    private fun getTestDuration(test: NodeId): String {
        val startTime = testsStartTimes[test] ?: return ROOT_SUITE
        val endTime = System.currentTimeMillis()
        return (endTime - startTime).toString()
    }

    private fun processFinishedSuites(messages: MutableList<ServiceMessageBuilder>) {
        val iterator = pendingFinishedSuites.iterator()
        while (iterator.hasNext()) {
            val suite = iterator.next()
            if (getNotFinishedChildren(suite).isEmpty()) {
                messages.add(createTestSuiteFinishedMessage(suite))
                iterator.remove()
            }
        }
    }

    private fun getNotFinishedChildren(suite: NodeId): Set<NodeId> =
        suitesToNotFinishedChildren[suite].orEmpty()

    private fun recursivelyInitContainingSuite(node: NodeId, messages: MutableList<ServiceMessageBuilder>) {
        val suite = node.parent
        if (suite == target) return // Already initialized

        // Pop all non-parent suites from stack and finish them
        while (suite != currentSuite && !suite.startsWith("$currentSuite$NAME_SEPARATOR")) {
            val lastSuite = suitesStack.removeLast()
            pendingFinishedSuites.add(lastSuite)
        }
        processFinishedSuites(messages)

        // Already initialized
        if (suite == currentSuite) return

        // Initialize parents
        recursivelyInitContainingSuite(suite, messages)

        // Initialize current suite
        messages.add(createTestSuiteStartedMessage(suite))
        recordSuiteChildStarted(suite)
        suitesStack.add(suite)
    }

    companion object {
        private val ESCAPED_SEPARATOR: String = File.separator.replace("\\", "\\\\")
        private val TARGET_PATH_PART_REGEX: Regex = Regex("[ $ESCAPED_SEPARATOR]target$ESCAPED_SEPARATOR")
        private const val ROOT_SUITE: String = "0"
        private const val NAME_SEPARATOR: String = "::"

        private val NodeId.name: String
            get() {
                return substringAfterLast(NAME_SEPARATOR)
            }

        private val NodeId.parent: NodeId
            get() {
                val parent = substringBeforeLast(NAME_SEPARATOR)
                return if (this == parent) ROOT_SUITE else parent
            }

        private fun createTestSuiteStartedMessage(suite: NodeId): ServiceMessageBuilder =
            ServiceMessageBuilder.testSuiteStarted(suite.name)
                .addAttribute("nodeId", suite)
                .addAttribute("parentNodeId", suite.parent)
                .addAttribute("locationHint", getTestModUrl(suite))

        private fun createTestSuiteFinishedMessage(suite: NodeId): ServiceMessageBuilder =
            ServiceMessageBuilder.testSuiteFinished(suite.name)
                .addAttribute("nodeId", suite)

        private fun createTestStartedMessage(test: NodeId): ServiceMessageBuilder =
            ServiceMessageBuilder.testStarted(test.name)
                .addAttribute("nodeId", test)
                .addAttribute("parentNodeId", test.parent)
                .addAttribute("locationHint", getTestFnUrl(test))

        private fun createTestFailedMessage(test: NodeId, failedMessage: String): ServiceMessageBuilder =
            ServiceMessageBuilder.testFailed(test.name)
                .addAttribute("nodeId", test)
                .addAttribute("message", "")
                .addAttribute("details", failedMessage)

        private fun createTestFinishedMessage(test: NodeId, duration: String): ServiceMessageBuilder =
            ServiceMessageBuilder.testFinished(test.name)
                .addAttribute("nodeId", test)
                .addAttribute("duration", duration)

        private fun createTestIgnoredMessage(test: NodeId): ServiceMessageBuilder =
            ServiceMessageBuilder.testIgnored(test.name)
                .addAttribute("nodeId", test)

        private fun createTestStdOutMessage(test: NodeId, stdout: String): ServiceMessageBuilder =
            ServiceMessageBuilder.testStdOut(test.name)
                .addAttribute("nodeId", test)
                .addAttribute("out", stdout)

        private fun createTestCountMessage(testCount: String): ServiceMessageBuilder =
            ServiceMessageBuilder("testCount")
                .addAttribute("count", testCount)

        private fun parseFailedTestOutput(output: String): FailedTestOutput {
            val partitionPredicate: (String) -> Boolean = { !it.trimStart().startsWith("thread", true) }
            val stdout = output.lineSequence().takeWhile(partitionPredicate).joinToString("\n")
            val failedMessage = output.lineSequence().dropWhile(partitionPredicate).joinToString("\n")
            return FailedTestOutput(stdout, failedMessage)
        }

        private data class LibtestSuiteMessage(
            val type: String,
            val event: String,
            val test_count: String
        ) {
            companion object {
                fun fromJson(json: JsonObject): LibtestSuiteMessage? {
                    if (json.getAsJsonPrimitive("type")?.asString != "suite") {
                        return null
                    }
                    return Gson().fromJson(json, LibtestSuiteMessage::class.java)
                }
            }
        }

        private data class LibtestTestMessage(
            val type: String,
            val event: String,
            val name: String,
            val stdout: String?
        ) {
            companion object {
                fun fromJson(json: JsonObject): LibtestTestMessage? {
                    if (json.getAsJsonPrimitive("type")?.asString != "test") {
                        return null
                    }
                    return Gson().fromJson(json, LibtestTestMessage::class.java)
                }
            }
        }
    }
}
