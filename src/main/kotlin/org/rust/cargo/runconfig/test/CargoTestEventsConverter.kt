/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.execution.testframework.sm.ServiceMessageBuilder
import com.intellij.execution.testframework.sm.runner.OutputToGeneralTestEventsConverter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil.unescapeStringCharacters
import com.intellij.openapi.util.text.StringUtil.unquoteString
import com.intellij.util.execution.ParametersListUtil
import jetbrains.buildServer.messages.serviceMessages.ServiceMessageVisitor
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.allPackages
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.cargo.runconfig.test.CargoTestEventsConverter.State.*
import org.rust.openapiext.JsonUtils.tryParseJsonObject
import org.rust.stdext.removeLast
import java.io.File

private typealias NodeId = String

class CargoTestEventsConverter(
    testFrameworkName: String,
    consoleProperties: TestConsoleProperties
) : OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val project: Project = consoleProperties.project
    private var converterState: State = START_MESSAGE

    private val suitesStack: MutableList<NodeId> = mutableListOf()
    private val target: NodeId get() = suitesStack.firstOrNull() ?: ROOT_SUITE
    private val currentSuite: NodeId get() = suitesStack.lastOrNull() ?: ROOT_SUITE

    private val suitesToNotFinishedChildren: MutableMap<NodeId, MutableSet<NodeId>> = hashMapOf()
    private val testsStartTimes: MutableMap<NodeId, Long> = hashMapOf()
    private val pendingFinishedSuites: MutableSet<NodeId> = linkedSetOf()

    private var doctestPackageCounter: Int = 0

    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        handleStartMessage(text)

        val escapedText = text.replace(NAME_RE) { it.value.replace("\\", "\\\\") }
        val jsonObject = tryParseJsonObject(escapedText)

        return when {
            jsonObject == null -> false
            handleTestMessage(jsonObject, outputType, visitor) -> true
            handleSuiteMessage(jsonObject, outputType, visitor) -> true
            else -> true // don't print unknown json messages
        }
    }

    private fun handleStartMessage(text: String) {
        when (converterState) {
            START_MESSAGE -> {
                val clean = text.trim().toLowerCase()
                converterState = when {
                    clean == "running" -> EXECUTABLE_NAME
                    clean == "doc-tests" -> DOCTESTS_PACKAGE_NAME
                    TARGET_PATH_PART in text -> {
                        val executableName = ParametersListUtil.parse(text)
                            .firstOrNull()
                            ?.substringAfterLast("/")
                            ?.substringBeforeLast(".")
                            ?.takeIf { it.isNotEmpty() }
                            ?: error("Can't parse the executable name")
                        suitesStack.add(executableName)
                        START_MESSAGE
                    }
                    else -> return
                }
            }
            EXECUTABLE_NAME -> {
                val executableName = text
                    .trim()
                    .substringAfterLast(project.toolchain?.fileSeparator ?: File.separator)
                    .substringBeforeLast(".")
                    .takeIf { it.isNotEmpty() }
                    ?: error("Can't parse the executable name")
                suitesStack.add(executableName)
                converterState = START_MESSAGE
            }
            DOCTESTS_PACKAGE_NAME -> {
                // package-name -> target_name-#idoctests
                // `rustdoc` produces package name instead of target name, but since only library targets can have
                // doctests we can find the corresponding lib target
                val packageName = text.trim()
                val libTargetName = findLibTargetForPackageName(project, packageName)?.normName
                val targetNameWithSuffix = "${libTargetName ?: packageName}-${doctestPackageCounter++}$DOCTESTS_SUFFIX"
                suitesStack.add(targetNameWithSuffix)
                converterState = START_MESSAGE
            }
        }
    }

    private fun handleTestMessage(
        jsonObject: JsonObject,
        outputType: Key<*>,
        visitor: ServiceMessageVisitor
    ): Boolean {
        val testMessage = LibtestTestMessage.fromJson(jsonObject)
            ?.let {
                // Parse `rustdoc` test name:
                // src/lib.rs - qualifiedName (line #i) -> qualifiedName (line #i)
                val qualifiedName = it.name.substringAfter(" - ")
                it.copy(name = "$target::$qualifiedName")
            } ?: return false
        val messages = createServiceMessagesFor(testMessage) ?: return false
        for (message in messages) {
            super.processServiceMessages(message.toString(), outputType, visitor)
        }
        return true
    }

    private fun handleSuiteMessage(
        jsonObject: JsonObject,
        outputType: Key<*>,
        visitor: ServiceMessageVisitor
    ): Boolean {
        val suiteMessage = LibtestSuiteMessage.fromJson(jsonObject) ?: return false
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
                if (!testMessage.stdout.isNullOrEmpty()) {
                    messages.add(createTestStdOutMessage(testMessage.name, testMessage.stdout))
                }
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

        // Pop all non-parent suites from the stack and finish them
        while (suite != currentSuite && !suite.startsWith("$currentSuite$NAME_SEPARATOR")) {
            val lastSuite = suitesStack.removeLast()
            pendingFinishedSuites.add(lastSuite)
        }
        processFinishedSuites(messages)

        // Already initialized
        if (suite == currentSuite) return

        // Initialize parents
        recursivelyInitContainingSuite(suite, messages)

        // Initialize the current suite
        messages.add(createTestSuiteStartedMessage(suite))
        recordSuiteChildStarted(suite)
        suitesStack.add(suite)
    }

    companion object {
        private const val TARGET_PATH_PART: String = "/target/"

        private const val ROOT_SUITE: String = "0"
        private const val NAME_SEPARATOR: String = "::"
        private const val DOCTESTS_SUFFIX = "doctests"

        private val NAME_RE: Regex = """"name":\s*"(?<name>[^"]+)"""".toRegex()

        private val LINE_NUMBER_RE: Regex = """\s+\(line\s+(?<line>\d+)\)\s*""".toRegex()

        private val ERROR_MESSAGE_RE: Regex =
            """thread '.*' panicked at '(assertion failed: `\(left (?<sign>.*) right\)`\s*left: `(?<left>.*?)`,\s*right: `(?<right>.*?)`(: )?)?(?<message>.*)',"""
                .toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))

        private val NodeId.name: String
            get() = if (contains(NAME_SEPARATOR)) {
                // target_name-xxxxxxxxxxxxxxxx::name1::name2 -> name2
                substringAfterLast(NAME_SEPARATOR)
            } else {
                // target_name-xxxxxxxxxxxxxxxx -> target_name
                val targetName = substringBeforeLast("-")
                // Add a tag to distinguish a doc-test suite from a lib suite
                if (endsWith(DOCTESTS_SUFFIX)) "$targetName (doc-tests)" else targetName
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
                .addAttribute("locationHint", CargoTestLocator.getTestUrl(suite))

        private fun createTestSuiteFinishedMessage(suite: NodeId): ServiceMessageBuilder =
            ServiceMessageBuilder.testSuiteFinished(suite.name)
                .addAttribute("nodeId", suite)

        private fun createTestStartedMessage(test: NodeId): ServiceMessageBuilder {
            // target_name::name1::name2 (line i) -> target_name::name1::name2#(i - 1)
            val name = test.replace(LINE_NUMBER_RE) {
                val line = it.groups["line"]?.value ?: error("Failed to find `line` capturing group")
                "#${line.toInt() - 1}"
            }
            return ServiceMessageBuilder.testStarted(test.name)
                .addAttribute("nodeId", test)
                .addAttribute("parentNodeId", test.parent)
                .addAttribute("locationHint", CargoTestLocator.getTestUrl(name))
        }

        private fun createTestFailedMessage(test: NodeId, failedMessage: String): ServiceMessageBuilder {
            val builder = ServiceMessageBuilder.testFailed(test.name)
                .addAttribute("nodeId", test)
                // TODO: pass backtrace here
                .addAttribute("details", failedMessage)
            val parseResult = parseErrorMessage(failedMessage)
            if (parseResult == null) {
                builder.addAttribute("message", "")
            } else {
                val (message, diff) = parseResult
                builder.addAttribute("message", message)
                if (diff != null) {
                    builder
                        .addAttribute("actual", diff.left)
                        .addAttribute("expected", diff.right)
                }
            }
            return builder
        }

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

        private fun parseErrorMessage(failedMessage: String): ErrorMessage? {
            val groups = ERROR_MESSAGE_RE.find(failedMessage)?.groups ?: return null
            val message = groups["message"]?.value ?: error("Failed to find `message` capturing group")

            val diff = if (groups["sign"]?.value == "==") {
                val left = groups["left"]?.value?.let(::unescape)
                val right = groups["right"]?.value?.let(::unescape)
                if (left != null && right != null) DiffResult(left, right) else null
            } else {
                null
            }

            return ErrorMessage(message, diff, null)
        }

        private fun findLibTargetForPackageName(
            project: Project,
            packageName: String
        ): CargoWorkspace.Target? = project
            .cargoProjects
            .allPackages
            .filter { it.origin == PackageOrigin.WORKSPACE }
            .mapNotNull { it.libTarget }
            .filter { it.pkg.name == packageName }
            .firstOrNull()
    }

    /**
     * The [CargoTestEventsConverter] can be in one of three states:
     * - [START_MESSAGE] -- processes messages pending the next start message ("running" / "doc-tests").
     * - [EXECUTABLE_NAME] -- the next message contains the name of the executable file.
     * - [DOCTESTS_PACKAGE_NAME] -- the next message contains the name of the package with doc-tests.
     */
    private enum class State {
        START_MESSAGE,
        EXECUTABLE_NAME,
        DOCTESTS_PACKAGE_NAME
    }
}

private fun unescape(s: String): String = unquoteString(unescapeStringCharacters(s))

private data class FailedTestOutput(val stdout: String, val failedMessage: String)
private data class ErrorMessage(val message: String, val diff: DiffResult?, val backtrace: String?)
private data class DiffResult(val left: String, val right: String)

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
