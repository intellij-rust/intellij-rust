/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("UnstableApiUsage")

package org.rustSlowTests.ide.actions

import com.intellij.build.events.impl.SuccessResultImpl
import com.intellij.execution.RunManager
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testFramework.TestDataProvider
import org.rust.MinRustcVersion
import org.rust.cargo.runconfig.buildtool.CargoBuildManager.lastBuildCommandLine
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CompositeCargoRunConfigurationProducer
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.fileTree
import org.rust.ide.actions.RsBuildAction
import org.rustSlowTests.cargo.runconfig.buildtool.CargoBuildTest

@MinRustcVersion("1.48.0")
class RsBuildActionTest : CargoBuildTest() {

    fun `test build first action`() {
        val testProject = fileTree {
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
                    rust("main.rs", """
                    fn main() {/*caret*/}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                    fn main() {}
                """)
                }
            }
        }.create()

        setUpSelectedConfigurationFromContext(testProject.fileWithCaret)
        performBuildAction()
        testBuildViewManager.waitFinished()

        val actualCommandLine = lastBuildCommandLine!!
        val expectedCommandLine = CargoCommandLine(
            "build",
            actualCommandLine.workingDirectory,
            listOf("--message-format=json-diagnostic-rendered-ansi", "--package", "first", "--bin", "first"),
            environmentVariables = BUILD_VARIABLES
        )
        assertEquals(expectedCommandLine, actualCommandLine)

        checkEvents {
            startBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo Command"
            )
            eventTree {
                startEvent(message = "Compiling first v0.1.0")
                finishEvent(
                    message = "Compiling first v0.1.0",
                    result = SuccessResultImpl()
                )
            }
            finishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        }
    }

    fun `test build second action`() {
        val testProject = fileTree {
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
                    rust("main.rs", """
                    fn main() {}
                """)
                }
            }

            dir("second") {
                toml("Cargo.toml", """
                    [package]
                    name = "second"
                    version = "0.1.0"
                    authors = []
                """)

                dir("src") {
                    rust("main.rs", """
                    fn main() {/*caret*/}
                """)
                }
            }
        }.create()

        setUpSelectedConfigurationFromContext(testProject.fileWithCaret)
        performBuildAction()
        testBuildViewManager.waitFinished()

        val actualCommandLine = lastBuildCommandLine!!
        val expectedCommandLine = CargoCommandLine(
            "build",
            actualCommandLine.workingDirectory,
            listOf("--message-format=json-diagnostic-rendered-ansi", "--package", "second", "--bin", "second"),
            environmentVariables = BUILD_VARIABLES
        )
        assertEquals(expectedCommandLine, actualCommandLine)

        checkEvents {
            startBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo Command"
            )
            eventTree {
                startEvent(message = "Compiling second v0.1.0")
                finishEvent(
                    message = "Compiling second v0.1.0",
                    result = SuccessResultImpl()
                )
            }
            finishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        }
    }

    fun `test build all action`() {
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
                    rust("main.rs", """
                    fn main() {}
                """)
                }
            }
        }.create()

        performBuildAction()
        testBuildViewManager.waitFinished()

        val actualCommandLine = lastBuildCommandLine!!
        val expectedCommandLine = CargoCommandLine(
            "build",
            actualCommandLine.workingDirectory,
            listOf("--message-format=json-diagnostic-rendered-ansi", "--all", "--all-targets"),
            environmentVariables = BUILD_VARIABLES
        )
        assertEquals(expectedCommandLine, actualCommandLine)

        checkEvents {
            startBuildEvent(
                message = "Build running...",
                buildTitle = "Run Cargo Command"
            )
            unordered {
                eventTree {
                    startEvent(message = "Compiling first v0.1.0")
                    finishEvent(
                        message = "Compiling first v0.1.0",
                        result = SuccessResultImpl()
                    )
                }
                eventTree {
                    startEvent(message = "Compiling second v0.1.0")
                    finishEvent(
                        message = "Compiling second v0.1.0",
                        result = SuccessResultImpl()
                    )
                }
            }
            finishBuildEvent(
                message = "Build successful",
                result = SuccessResultImpl()
            )
        }
    }

    fun `test build does not use root privileges`() {
        val testProject = fileTree {
            toml("Cargo.toml", """
                [package]
                name = "foo"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", """
                fn main() {/*caret*/}
            """)
            }
        }.create()

        setUpSelectedConfigurationFromContext(testProject.fileWithCaret, withSudo = true)
        performBuildAction()
        testBuildViewManager.waitFinished()

        val actualCommandLine = lastBuildCommandLine!!
        assertFalse(actualCommandLine.withSudo)
    }

    private fun performBuildAction() {
        val action = ActionManager.getInstance().getAction("Rust.Build") as RsBuildAction
        action.performForContext(TestDataProvider(project))
    }

    private fun setUpSelectedConfigurationFromContext(fileWithCaret: String, withSudo: Boolean = false) {
        val runManager = RunManager.getInstance(project) as RunManagerImpl
        myFixture.configureFromTempProjectFile(fileWithCaret)
        val producer = CompositeCargoRunConfigurationProducer()
        val settings = createRunnerAndConfigurationSettingsFromContext(producer, null).apply {
            (configuration as? CargoCommandConfiguration)?.withSudo = withSudo
        }
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
    }

    companion object {
        private val BUILD_VARIABLES: EnvironmentVariablesData =
            EnvironmentVariablesData.create(
                mapOf(
                    "CARGO_TERM_PROGRESS_WHEN" to "always",
                    "CARGO_TERM_PROGRESS_WIDTH" to "1000"
                ),
                true
            )
    }
}
