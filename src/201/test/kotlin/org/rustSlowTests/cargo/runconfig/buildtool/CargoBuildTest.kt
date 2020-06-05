/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.events.*
import com.intellij.build.process.BuildProcessHandler
import com.intellij.execution.filters.Filter
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.Consumer
import org.rust.cargo.runconfig.buildtool.CargoBuildEventsConverter.Companion.RUSTC_MESSAGE_GROUP
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockBuildProgressListener
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockProgressIndicator
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.testBuildId
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.runconfig.buildtool.MockBuildProgressListener
import org.rust.cargo.runconfig.buildtool.MockProgressIndicator
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase
import java.util.function.Supplier


abstract class CargoBuildTest : RunConfigurationTestBase() {

    override fun shouldRunTest(): Boolean = System.getenv("CI") == null

    override fun setUp() {
        super.setUp()
        testBuildId = Any()
        mockBuildProgressListener = MockBuildProgressListener()
        mockProgressIndicator = MockProgressIndicator()
    }

    override fun tearDown() {
        super.tearDown()
        testBuildId = null
        mockBuildProgressListener = null
        mockProgressIndicator = null
    }

    @Suppress("EqualsOrHashCode")
    companion object {

        @JvmStatic
        protected fun checkResult(
            actual: CargoBuildResult,
            message: String = "Build finished",
            errors: Int = 0,
            warnings: Int = 0,
            succeeded: Boolean = true,
            canceled: Boolean = false
        ) {
            assertEquals(message, actual.message)
            assertEquals(errors, actual.errors)
            assertEquals(warnings, actual.warnings)
            assertEquals(succeeded, actual.succeeded)
            assertEquals(canceled, actual.canceled)
        }

        fun checkEvents(vararg expectedEvents: BuildEvent) {
            val actualEvents = mockBuildProgressListener?.eventHistory.orEmpty().filter { it !is OutputBuildEvent }
            assertEquals(expectedEvents.size, actualEvents.size)
            for ((expected, actual) in expectedEvents.zip(actualEvents)) {
                assertEquals(expected, actual)
            }
        }

        @JvmStatic
        protected fun checkProgressIndicator(vararg expectedTexts: String) {
            val actualTexts = mockProgressIndicator
                ?.textHistory
                .orEmpty()
                .windowed(2, 2) { list ->
                    list.filterNot { it.isNullOrEmpty() }.joinToString(" ")
                }
                // Eliminate consecutive duplicates
                .fold(emptyList<String>()) { result, value ->
                    if (result.isNotEmpty() && result.last() == value) result else result + value
                }
            assertEquals(expectedTexts.toList(), actualTexts)
        }

        protected abstract class MyBuildEvent(
            private val id: Any,
            private val parentId: Any?,
            private val message: String
        ) : BuildEvent {
            override fun getId(): Any = id
            override fun getParentId(): Any? = parentId
            override fun getMessage(): String = message
            override fun getEventTime(): Long = 0
            override fun getDescription(): String? = null
            override fun getHint(): String? = null

            override fun equals(other: Any?): Boolean = when {
                this === other -> true
                other !is BuildEvent -> false
                parentId != other.parentId -> false
                message != other.message -> false
                else -> true
            }

            override fun toString(): String = "MyBuildEvent(id=$id, parentId=$parentId, message='$message')"
        }

        protected open class MyStartEvent(
            id: Any,
            parentId: Any?,
            message: String
        ) : MyBuildEvent(id, parentId, message), StartEvent {
            override fun equals(other: Any?): Boolean = when {
                !super.equals(other) -> false
                other !is StartEvent -> false
                id != other.id -> false
                else -> true
            }

            override fun toString(): String = "MyStartEvent(${super.toString()})"
        }

        protected open class MyFinishEvent(
            id: Any,
            parentId: Any?,
            message: String,
            private val result: EventResult
        ) : MyBuildEvent(id, parentId, message), FinishEvent {
            override fun getResult(): EventResult = result

            override fun equals(other: Any?): Boolean = when {
                !super.equals(other) -> false
                other !is FinishEvent -> false
                id != other.id -> false
                result.javaClass != other.result.javaClass -> false
                else -> true
            }

            override fun toString(): String = "MyFinishEvent(${super.toString()}, result=$result)"
        }

        protected class MyStartBuildEvent(
            message: String,
            private val buildTitle: String
        ) : MyStartEvent(testBuildId!!, null, message), StartBuildEvent {
            override fun getBuildTitle(): String = buildTitle
            override fun getAttachedConsoleConsumer(): Consumer<ConsoleView>? = null
            override fun getRestartActions(): Array<AnAction> = emptyArray()
            override fun getExecutionEnvironment(): ExecutionEnvironment? = null
            override fun getWorkingDir(): String = ""
            override fun getProcessHandler(): BuildProcessHandler? = null
            override fun getContentDescriptorSupplier(): Supplier<RunContentDescriptor>? = null
            override fun getExecutionFilters(): Array<Filter> = emptyArray()

            override fun equals(other: Any?): Boolean = when {
                !super.equals(other) -> false
                other !is StartBuildEvent -> false
                buildTitle != other.buildTitle -> false
                else -> true
            }

            override fun toString(): String = "MyStartBuildEvent(${super.toString()}, buildTitle='$buildTitle')"
        }

        protected class MyFinishBuildEvent(
            message: String,
            result: EventResult
        ) : MyFinishEvent(testBuildId!!, null, message, result), FinishBuildEvent {
            override fun equals(other: Any?): Boolean = when {
                !super.equals(other) -> false
                other !is FinishBuildEvent -> false
                else -> true
            }

            override fun toString(): String = "MyFinishBuildEvent(${super.toString()})"
        }

        protected open class MyMessageEvent(
            parentId: Any,
            message: String,
            private val kind: MessageEvent.Kind
        ) : MyBuildEvent(Any(), parentId, message), MessageEvent {
            override fun getGroup(): String = RUSTC_MESSAGE_GROUP
            override fun getKind(): MessageEvent.Kind = kind
            override fun getNavigatable(project: Project): Navigatable? = null
            override fun getResult(): MessageEventResult = MessageEventResult { kind }

            override fun equals(other: Any?): Boolean = when {
                !super.equals(other) -> false
                other !is MessageEvent -> false
                kind != other.kind -> false
                kind != other.result.kind -> false
                else -> true
            }

            override fun toString(): String = "MyMessageEvent(${super.toString()}, kind=$kind)"
        }
    }
}
