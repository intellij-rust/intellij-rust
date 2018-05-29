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
import jetbrains.buildServer.messages.serviceMessages.*

data class LibtestSuiteMessage(
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

data class LibtestTestMessage(
    val type: String,
    val event: String,
    val name: String,
    val stdout: String
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

class CargoTestEventsConverter(consoleProperties: TestConsoleProperties, val testFrameworkName: String) :
    OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val parser = JsonParser()
    private var first = true
    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {

        if (first) {
            visitor.visitCompilationStarted(CompilationStarted("rustc"))
            first = false
            return true
        }
        if (text.trim().startsWith("finished", true)) {
            visitor.visitCompilationFinished(CompilationFinished("rustc"))
            return true
        }
        val jsonElement: JsonElement
        try {
            jsonElement = parser.parse(text)
            if (!jsonElement.isJsonObject) return false
        } catch (es: JsonSyntaxException) {
            return false
        }
        if (handleTestMessage(jsonElement, outputType, visitor)) return true
        if (handleSuiteMessage(jsonElement, outputType, visitor)) return true
        return false
    }

    private fun handleSuiteMessage(jsonElement: JsonElement, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        val suiteMessage = LibtestSuiteMessage.fromJson(jsonElement.asJsonObject) ?: return false
        val messages = createServiceMessagesFor(suiteMessage) ?: return false
        messages
            .map { it.toString() }
            .forEach { super.processServiceMessages(it, outputType, visitor) }
        return true
    }

    private fun handleTestMessage(jsonElement: JsonElement, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        val testMessage = LibtestTestMessage.fromJson(jsonElement.asJsonObject) ?: return false
        val messages = createServiceMessagesFor(testMessage) ?: return false

        messages
            .map { it.toString() }
            .forEach { super.processServiceMessages(it, outputType, visitor) }
        return true
    }

    private fun createServiceMessagesFor(message: LibtestTestMessage): List<ServiceMessageBuilder>? =
        when (message.event) {
            "started" -> listOf(ServiceMessageBuilder.testStarted(message.name)
                .addAttribute("locationHint", CargoTestLocator.getTestFnUrl(message.name)))
            "ok" -> listOf(ServiceMessageBuilder.testFinished(message.name))
            "failed" -> {
                val message1 = ServiceMessageBuilder.testFailed(message.name)
                    .addAttribute("message", message.stdout)

                val message2 = ServiceMessageBuilder.testFinished(message.name)
                listOf(message1, message2)
            }
            "ignored" -> listOf(ServiceMessageBuilder.testIgnored(message.name))
            else -> null
        }

    private fun createServiceMessagesFor(message: LibtestSuiteMessage): List<ServiceMessageBuilder>? =
        when (message.event) {
            "started" -> {
                val message1 = ServiceMessageBuilder.testSuiteStarted(testFrameworkName)
                val message2 = ServiceMessageBuilder("testCount")
                    .addAttribute("count", message.test_count)
                listOf(message1, message2)
            }
            "ok", "failed" -> listOf(ServiceMessageBuilder.testSuiteFinished(testFrameworkName))
            else -> null
        }
}
