/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.BuildDescriptor
import com.intellij.build.BuildViewManager
import com.intellij.build.DefaultBuildDescriptor
import com.intellij.build.events.*
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.testFramework.replaceService
import org.rust.RsBundle
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockProgressIndicator
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.runconfig.buildtool.MockProgressIndicator
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase
import org.rustSlowTests.cargo.runconfig.buildtool.TestBuildViewManager.*

@Suppress("EqualsOrHashCode")
abstract class CargoBuildTest : RunConfigurationTestBase() {

    protected lateinit var testBuildViewManager: TestBuildViewManager

    override fun setUp() {
        super.setUp()
        testBuildViewManager = TestBuildViewManager(project)
        project.replaceService(BuildViewManager::class.java, testBuildViewManager, testRootDisposable)
        mockProgressIndicator = MockProgressIndicator()
    }

    override fun tearDown() {
        super.tearDown()
        mockProgressIndicator = null
    }

    protected fun checkEvents(
        build: EventTreeBuilder.() -> Unit
    ) {
        val events = mutableListOf<EventTreeNode>()
        val builder = EventTreeBuilder(events)
        builder.build()
        val expectedEventTree = if (events.isNotEmpty()) ParentEventNode(events) else null
        assertEquals(expectedEventTree, testBuildViewManager.rootNode)
    }

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

        @JvmStatic
        protected fun checkProgressIndicator(vararg expectedTexts: String) {
            val progressPrefix = "Building... "
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
                .map { line ->
                    if (line.startsWith(progressPrefix)) {
                        val tasksText = line.substringAfter(progressPrefix)
                        val sortedTasksTest = tasksText.split(", ").sorted().joinToString(", ")
                        progressPrefix + sortedTasksTest
                    } else {
                        line
                    }
                }
            assertEquals(expectedTexts.toList(), actualTexts)
        }
    }

    private abstract class MyBuildEvent(
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
            message != other.message -> false
            else -> true
        }

        override fun toString(): String = "MyBuildEvent(id=$id, parentId=$parentId, message='$message')"
    }

    private open class MyStartEvent(
        id: Any,
        parentId: Any?,
        message: String
    ) : MyBuildEvent(id, parentId, message), StartEvent {
        override fun equals(other: Any?): Boolean = when {
            !super.equals(other) -> false
            other !is StartEvent -> false
            else -> true
        }

        override fun toString(): String = "MyStartEvent(${super.toString()})"
    }

    private open class MyFinishEvent(
        id: Any,
        parentId: Any?,
        message: String,
        private val result: EventResult
    ) : MyBuildEvent(id, parentId, message), FinishEvent {
        override fun getResult(): EventResult = result

        override fun equals(other: Any?): Boolean = when {
            !super.equals(other) -> false
            other !is FinishEvent -> false
            result.javaClass != other.result.javaClass -> false
            else -> true
        }

        override fun toString(): String = "MyFinishEvent(${super.toString()}, result=$result)"
    }

    private class MyStartBuildEvent(
        id: Any,
        message: String,
        val buildTitle: String
    ) : MyStartEvent(id, null, message), StartBuildEvent {

        override fun getBuildDescriptor(): BuildDescriptor =
            DefaultBuildDescriptor(Any(), "", "", 0)

        override fun equals(other: Any?): Boolean = when {
            !super.equals(other) -> false
            other !is StartBuildEvent -> false
            buildTitle != other.buildDescriptor.title -> false
            else -> true
        }

        override fun toString(): String = "MyStartBuildEvent(${super.toString()}, buildTitle='$buildTitle')"
    }

    private class MyFinishBuildEvent(
        id: Any,
        message: String,
        result: EventResult
    ) : MyFinishEvent(id, null, message, result), FinishBuildEvent {
        override fun equals(other: Any?): Boolean = when {
            !super.equals(other) -> false
            other !is FinishBuildEvent -> false
            else -> true
        }

        override fun toString(): String = "MyFinishBuildEvent(${super.toString()})"
    }

    private open class MyMessageEvent(
        id: Any,
        parentId: Any,
        message: String,
        private val kind: MessageEvent.Kind
    ) : MyBuildEvent(id, parentId, message), MessageEvent {
        override fun getGroup(): String = RsBundle.message("rust.compiler")
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

    protected data class EventTreeBuilder(
        private val events: MutableList<EventTreeNode> = mutableListOf(),
        private val unorderedGroupId: Int = -1,
        private val id: Any = Any(),
        private val parentId: Any? = null
    ) {

        fun eventTree(build: EventTreeBuilder.() -> Unit) {
            val builder = EventTreeBuilder(parentId = id)
            builder.build()
            events += ParentEventNode(builder.events, unorderedGroupId)
        }

        fun unordered(build: EventTreeBuilder.() -> Unit) {
            val builder = copy(unorderedGroupId = unorderedGroupCounter)
            unorderedGroupCounter++
            builder.build()
        }

        fun startBuildEvent(message: String, buildTitle: String) {
            event(MyStartBuildEvent(id = id, message = message, buildTitle = buildTitle))
        }

        fun finishBuildEvent(message: String, result: EventResult) {
            event(MyFinishBuildEvent(id = id, message = message, result = result))
        }

        fun startEvent(message: String) {
            event(MyStartEvent(id = id, parentId = parentId, message = message))
        }

        fun finishEvent(message: String, result: EventResult) {
            event(MyFinishEvent(id = id, parentId = parentId, message = message, result = result))
        }

        fun messageEvent(message: String, kind: MessageEvent.Kind) {
            eventTree {
                event(MyMessageEvent(id = id, parentId = parentId!!, message = message, kind = kind))
            }
        }

        fun event(e: BuildEvent) {
            events += SingleEventNode(e, unorderedGroupId)
        }

        companion object {
            private var unorderedGroupCounter = 0
        }
    }
}
