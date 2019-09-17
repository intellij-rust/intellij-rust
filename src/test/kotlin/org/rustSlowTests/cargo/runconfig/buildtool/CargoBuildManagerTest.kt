/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rustSlowTests.cargo.runconfig.buildtool

import com.intellij.build.events.*
import com.intellij.build.events.impl.FailureResultImpl
import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.build.process.BuildProcessHandler
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.filters.Filter
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.Consumer
import org.rust.MinRustcVersion
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.buildtool.*
import org.rust.cargo.runconfig.buildtool.CargoBuildEventsConverter.Companion.RUSTC_MESSAGE_GROUP
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.getBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockBuildProgressListener
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.mockProgressIndicator
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.testBuildId
import org.rust.fileTree
import org.rustSlowTests.cargo.runconfig.RunConfigurationTestBase
import java.util.function.Supplier

@MinRustcVersion("1.32.0")
class CargoBuildManagerTest : RunConfigurationTestBase() {

    override fun shouldRunTest(): Boolean = false

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

    fun `test build successful`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project(bin)"
        )
    }

    fun `test build successful with warning`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() {}
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 1,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Function is never used: `foo`",
                kind = MessageEvent.Kind.WARNING
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build failed`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() { 0 }
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build failed (multiple errors)`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() {
                        1 + "1";
                        2 + "2";
                        3 + "3";
                    }
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 3,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Cannot add `&str` to `{integer}`",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build canceled`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        mockProgressIndicator?.cancel()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build canceled",
            errors = 0,
            warnings = 0,
            succeeded = false,
            canceled = true
        )

        checkEvents()

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish..."
        )
    }

    fun `test build errors in multiple files`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("foo.rs", """
                    fn foo() { 0 }
                """)
                rust("bar.rs", """
                    fn bar() { 0 }
                """)
                rust("lib.rs", """
                    mod foo;
                    mod bar;
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 2,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build lib successful bin successful`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
                rust("main.rs", """
                    fn main() {}
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project",
            "Building... project(bin)"
        )
    }

    fun `test build lib successful bin failed`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn _foo() {}
                """)
                rust("main.rs", """
                    fn main() { 0 }
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project",
            "Building... project(bin)"
        )
    }

    fun `test build lib failed bin skipped`() {
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "project"
                version = "0.1.0"
                authors = []
            """)

            dir("src") {
                rust("lib.rs", """
                    fn foo() { 0 }
                """)
                rust("main.rs", """
                    fn main() { 0 }
                """)
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0"
            ),
            MyMessageEvent(
                parentId = "project 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "project 0.1.0",
                parentId = testBuildId,
                message = "Compiling project v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... project"
        )
    }

    fun `test build multiple packages first successful second successful`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _foo() {}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []
                    
                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() {}
                """)
                }
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build finished",
            errors = 0,
            warnings = 0,
            succeeded = true,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyStartEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0"
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0",
                result = SuccessResultImpl()
            ),
            MyFinishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first",
            "Building... second"
        )
    }

    fun `test build multiple packages first successful second failed`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _foo() {}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []
                    
                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() { 0 }
                """)
                }
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyStartEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0"
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = SuccessResultImpl()
            ),
            MyMessageEvent(
                parentId = "second 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "second 0.1.0",
                parentId = testBuildId,
                message = "Compiling second v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first",
            "Building... second"
        )
    }

    fun `test build multiple packages first failed second skipped`() {
        fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [
                    "first",
                    "second",
                ]
            """)

            dir("first") {
                toml("Cargo.toml", """
                    [package]
                    name = "first"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn foo() { 0 }
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []
                    
                    [dependencies]
                    first = { path = "../first" }
                """)

                dir("src") {
                    rust("lib.rs", """
                    fn _bar() { 0 }
                """)
                }
            }
        }.create()
        val buildConfiguration = createBuildConfiguration()
        val buildResult = CargoBuildManager.build(buildConfiguration).get()

        checkResult(
            buildResult,
            message = "Build failed",
            errors = 1,
            warnings = 0,
            succeeded = false,
            canceled = false
        )

        checkEvents(
            MyStartBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo command"
            ),
            MyStartEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0"
            ),
            MyMessageEvent(
                parentId = "first 0.1.0",
                message = "Mismatched types",
                kind = MessageEvent.Kind.ERROR
            ),
            MyFinishEvent(
                id = "first 0.1.0",
                parentId = testBuildId,
                message = "Compiling first v0.1.0",
                result = FailureResultImpl(null as Throwable?)
            ),
            MyFinishBuildEvent(
                message = "Build failed",
                result = FailureResultImpl(null as Throwable?)
            )
        )

        checkProgressIndicator(
            "Building...",
            "Waiting for the current build to finish...",
            "Building... first"
        )
    }

    private fun createBuildConfiguration(): CargoBuildConfiguration {
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        val runner = ProgramRunner.findRunnerById(CargoCommandRunner.RUNNER_ID)!!
        val runManager = RunManager.getInstance(project) as RunManagerImpl
        val configuration = getBuildConfiguration(createConfiguration())!!
        val settings = RunnerAndConfigurationSettingsImpl(runManager, configuration)
        val environment = ExecutionEnvironment(executor, runner, settings, project)
        return CargoBuildConfiguration(configuration, environment)
    }

    @Suppress("EqualsOrHashCode")
    companion object {
        private fun checkResult(
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

        private fun checkEvents(vararg expectedEvents: BuildEvent) {
            val actualEvents = mockBuildProgressListener?.eventHistory.orEmpty().filter { it !is OutputBuildEvent }
            assertEquals(expectedEvents.size, actualEvents.size)
            for ((expected, actual) in expectedEvents.zip(actualEvents)) {
                assertEquals(expected, actual)
            }
        }

        private fun checkProgressIndicator(vararg expectedTexts: String) {
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

        abstract class MyBuildEvent(
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

        private open class MyStartEvent(
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
                id != other.id -> false
                result.javaClass != other.result.javaClass -> false
                else -> true
            }

            override fun toString(): String = "MyFinishEvent(${super.toString()}, result=$result)"
        }

        private class MyStartBuildEvent(
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

        private class MyFinishBuildEvent(
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

        private open class MyMessageEvent(
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
