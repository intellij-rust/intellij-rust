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
                ?: error("Failed to parse LibtestSuiteMessage from $json")
        }
    }
}

data class LibtestTestMessage(
    val type: String,
    val event: String,
    val name: String
) {
    companion object {
        fun fromJson(json: JsonObject): LibtestTestMessage? {
            if (json.getAsJsonPrimitive("type")?.asString != "test") {
                return null
            }

            return Gson().fromJson(json, LibtestTestMessage::class.java)
                ?: error("Failed to parse LibtestTestMessage from $json")
        }
    }
}

class CargoTestEventsConverter(consoleProperties: TestConsoleProperties, val testFrameworkName: String) :
    OutputToGeneralTestEventsConverter(testFrameworkName, consoleProperties) {
    private val parser = JsonParser()
    private var first = true
    override fun processServiceMessages(text: String, outputType: Key<*>, visitor: ServiceMessageVisitor): Boolean {
        try {
            if (first) {
                visitor.visitCompilationStarted(CompilationStarted("rustc"))
                first = false
                return true
            }
            if (text.trim().startsWith("finished", true)) {
                visitor.visitCompilationFinished(CompilationFinished("rustc"))
                return true
            }
            val jsonElement = parser.parse(text)
            if (!jsonElement.isJsonObject) return false
            val suiteMessage = LibtestSuiteMessage.fromJson(jsonElement.asJsonObject)
            return if (suiteMessage == null) {
                handleTestMessage(jsonElement, visitor)
            } else {
                handleSuiteMessage(suiteMessage, visitor)
            }
        } catch (es: JsonSyntaxException) {
            return false
        }
    }

    private fun handleSuiteMessage(suiteMessage: LibtestSuiteMessage, visitor: ServiceMessageVisitor): Boolean {
        when (suiteMessage.event) {
            "started" -> {
                visitor.visitTestSuiteStarted(TestSuiteStarted(testFrameworkName))
                val messageBuilder = ServiceMessageBuilder("testCount")
                    .addAttribute("count", suiteMessage.test_count)
                    .toString()
                ServiceMessage.parse(messageBuilder)?.visit(visitor)
            }
            "ok" -> visitor.visitTestSuiteFinished(TestSuiteFinished(testFrameworkName))
            "failed" -> visitor.visitTestSuiteFinished(TestSuiteFinished(testFrameworkName))
            else -> return false
        }
        return true
    }

    private fun handleTestMessage(jsonElement: JsonElement, visitor: ServiceMessageVisitor): Boolean {
        val testMessage = LibtestTestMessage.fromJson(jsonElement.asJsonObject) ?: return false
        when (testMessage.event) {
            "started" -> {
                val messageBuilder = ServiceMessageBuilder.testStarted(testMessage.name)
                    .addAttribute("locationHint", CargoTestLocator.getTestFnUrl(testMessage.name))
                    .toString()
                ServiceMessage.parse(messageBuilder)?.visit(visitor)
            }
            "ok" -> visitor.visitTestFinished(TestFinished(testMessage.name, -1))
            "failed" -> {
                var message = ServiceMessageBuilder.testFailed(testMessage.name)
                    .addAttribute("message", "Please look in stderr")
                    .toString()
                ServiceMessage.parse(message)?.visit(visitor)

                message = ServiceMessageBuilder.testFinished(testMessage.name)
                    .toString()

                ServiceMessage.parse(message)?.visit(visitor)
            }
            "ignored" -> visitor.visitTestIgnored(TestIgnored(testMessage.name, ""))
            else -> return false
        }
        return true
    }
}
