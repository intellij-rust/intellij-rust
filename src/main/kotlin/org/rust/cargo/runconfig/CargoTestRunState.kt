/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig

import com.google.common.annotations.VisibleForTesting
import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.Executor
import com.intellij.execution.configuration.EnvironmentVariablesData
import com.intellij.execution.filters.BrowserHyperlinkInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.autotest.ToggleAutoTestAction
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationType
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlChunk
import org.jetbrains.annotations.Nls
import org.rust.RsBundle
import org.rust.cargo.runconfig.buildtool.CargoPatch
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.console.CargoTestConsoleBuilder
import org.rust.cargo.runconfig.test.CargoTestConsoleProperties.Companion.TEST_TOOL_WINDOW_SETTING_KEY
import org.rust.cargo.toolchain.CargoCommandLine
import org.rust.cargo.toolchain.RsToolchainBase.Companion.RUSTC_BOOTSTRAP
import org.rust.cargo.toolchain.RustChannel
import org.rust.cargo.util.parseSemVer
import org.rust.ide.notifications.showBalloon

class CargoTestRunState(
    environment: ExecutionEnvironment,
    runConfiguration: CargoCommandConfiguration,
    config: CargoCommandConfiguration.CleanConfiguration.Ok
) : CargoRunStateBase(environment, runConfiguration, config) {
    private val cargoTestPatch: CargoPatch = { commandLine ->
        val rustcVersion = cargoProject?.rustcInfo?.version
        // Stable Rust test framework does not support `-Z unstable-options --format json` since 1.70.0-beta
        // (https://github.com/rust-lang/rust/pull/109044)
        val requiresRustcBootstrap = !(rustcVersion != null
            && (rustcVersion.channel == RustChannel.NIGHTLY
            || rustcVersion.channel == RustChannel.DEV
            || rustcVersion.semver < RUSTC_1_70_BETA))
        val environmentVariables = if (requiresRustcBootstrap) {
            if (!PropertiesComponent.getInstance().getBoolean(DO_NOT_SHOW_KEY, false)) {
                showRustcBootstrapWarning(project)
            }
            val oldVariables = commandLine.environmentVariables
            EnvironmentVariablesData.create(
                oldVariables.envs + mapOf(RUSTC_BOOTSTRAP to "1"),
                oldVariables.isPassParentEnvs
            )
        } else {
            commandLine.environmentVariables
        }

        // TODO: always pass `withSudo` when `com.intellij.execution.process.ElevationService` supports error stream redirection
        // https://github.com/intellij-rust/intellij-rust/issues/7320
        if (commandLine.withSudo) {
            val message = if (SystemInfo.isWindows) {
                RsBundle.message("notification.run.tests.as.root.windows")
            } else {
                RsBundle.message("notification.run.tests.as.root.unix")
            }
            project.showBalloon(message, NotificationType.WARNING)
        }

        commandLine.copy(
            additionalArguments = patchArgs(commandLine),
            environmentVariables = environmentVariables,
            emulateTerminal = false,
            withSudo = false
        )
    }

    init {
        consoleBuilder = CargoTestConsoleBuilder(environment.runProfile as CargoCommandConfiguration, environment.executor)
        commandLinePatches.add(cargoTestPatch)
        createFilters(cargoProject).forEach { consoleBuilder.addFilter(it) }
    }

    override fun execute(executor: Executor, runner: ProgramRunner<*>): ExecutionResult {
        val processHandler = startProcess()
        val console = createConsole(executor)
        console?.attachToProcess(processHandler)
        return DefaultExecutionResult(console, processHandler).apply { setRestartActions(ToggleAutoTestAction()) }
    }

    companion object {
        // Stable Rust test framework does not support `-Z unstable-options --format json` since 1.70.0-beta
        // (https://github.com/rust-lang/rust/pull/109044)
        private val RUSTC_1_70_BETA = "1.70.0-beta".parseSemVer()

        private const val DO_NOT_SHOW_KEY: String = "org.rust.cargo.test.rustc.bootstrap.do.not.show"

        private const val CHANGES_URL: String = "https://blog.rust-lang.org/2023/06/01/Rust-1.70.0.html#enforced-stability-in-the-test-cli"

        private fun showRustcBootstrapWarning(project: Project) {
            @NlsSafe val RUSTC_BOOTSSTRAP = "${RUSTC_BOOTSTRAP}=1"
            @Nls val content = RsBundle.message("rustc.bootstrap.warning", HtmlChunk.link("changes", RsBundle.message("rust.1.70.0.stable")), HtmlChunk.text(RsBundle.message("cargo.test")).bold(), HtmlChunk.text(RUSTC_BOOTSSTRAP).bold(), HtmlChunk.br(), HtmlChunk.link("disable", RsBundle.message("disabling.the.test.tool.window")))
            project.showBalloon(
                RsBundle.message("notification.title.potentially.inconsistent.build.test.results"),
                content,
                NotificationType.WARNING,
                null
            ) { notification, event ->
                when (event.description) {
                    "changes" -> BrowserHyperlinkInfo(CHANGES_URL).navigate(project)
                    "disable" -> {
                        notification.expire()
                        AdvancedSettings.setBoolean(TEST_TOOL_WINDOW_SETTING_KEY, false)
                        showConfirmationInfo(project)
                    }
                }
            }

            PropertiesComponent.getInstance().setValue(DO_NOT_SHOW_KEY, true)
        }

        private fun showConfirmationInfo(project: Project) {
            @Nls val content = RsBundle.message("the.0.tool.window.was.disabled.1.2", HtmlChunk.text(RsBundle.message("test")).bold(), HtmlChunk.br(), HtmlChunk.link("revert", RsBundle.message("revert")))
            project.showBalloon(
                "",
                content,
                NotificationType.INFORMATION,
                null
            ) { notification, event ->
                when (event.description) {
                    "revert" -> {
                        notification.expire()
                        AdvancedSettings.setBoolean(TEST_TOOL_WINDOW_SETTING_KEY, true)
                    }
                }
            }
        }

        @VisibleForTesting
        fun patchArgs(commandLine: CargoCommandLine): List<String> {
            val (pre, post) = commandLine.splitOnDoubleDash()
                .let { (pre, post) -> pre.toMutableList() to post.toMutableList() }

            val noFailFastOption = "--no-fail-fast"
            if (noFailFastOption !in pre) {
                pre.add(noFailFastOption)
            }

            val unstableOption = "-Z"
            if (unstableOption !in post) {
                post.add(unstableOption)
                post.add("unstable-options")
            }

            addFormatJsonOption(post, "--format", "json")
            post.add("--show-output")

            return if (post.isEmpty()) pre else pre + "--" + post
        }
    }
}
