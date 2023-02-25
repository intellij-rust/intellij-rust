/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.CommandLineState
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.ExecutionSearchScopes
import com.intellij.util.ArrayUtil
import com.intellij.util.Consumer
import com.intellij.util.ui.StatusText
import com.jetbrains.cidr.cpp.CLionProfilingBundle
import com.jetbrains.cidr.cpp.profiling.*
import com.jetbrains.cidr.cpp.profiling.ui.MemoryProfileOutputPanel
import com.jetbrains.cidr.cpp.valgrind.*
import com.jetbrains.cidr.cpp.valgrind.actions.EditValgrindSettingsAction
import com.jetbrains.cidr.lang.toolchains.CidrToolEnvironment
import org.rust.cargo.runconfig.CargoCommandConfigurationExtension
import org.rust.cargo.runconfig.ConfigurationExtensionContext
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.toolchain.wsl.RsWslToolchain
import org.rust.clion.valgrind.legacy.RsValgrindRunnerLegacy
import org.rust.openapiext.isInternal
import org.rust.openapiext.isUnitTestMode
import org.rust.openapiext.showSettingsDialog
import java.io.File
import java.io.IOException

private val LOG: Logger = logger<RsValgrindConfigurationExtension>()

class RsValgrindConfigurationExtension : CargoCommandConfigurationExtension() {
    override fun isApplicableFor(configuration: CargoCommandConfiguration): Boolean = true

    override fun isEnabledFor(
        applicableConfiguration: CargoCommandConfiguration,
        runnerSettings: RunnerSettings?
    ): Boolean = isEnabledFor(applicableConfiguration)

    override fun patchCommandLine(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        cmdLine: GeneralCommandLine,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in VALGRIND_RUNNER_IDS) return
        val toolchain = configuration.clean().ok?.toolchain ?: return

        val programPath = toolchain.toLocalPath(cmdLine.exePath)
        if (!File(programPath).exists()) {
            throw ExecutionException("File not found: $programPath")
        }

        val project = configuration.project
        val valgrindPath = toolchain.toLocalPath(ValgrindSettings.getInstance().valgrindPath)
        if (StringUtil.isEmpty(valgrindPath) || !File(valgrindPath).exists()) {
            project.showSettingsDialog<ValgrindConfigurable>()
            return
        }

        try {
            val outputFile = FileUtil.createTempFile("valgrind", null, true)
            val outputFilePath = toolchain.toRemotePath(outputFile.absolutePath)
            cmdLine.exePath = valgrindPath
            // scheme of command line arguments
            // <valgrind-arguments> <program> <program-arguments>
            val parametersBuilder = ValgrindCommandLineParametersBuilder()
            val valgrindParameters = parametersBuilder.build(outputFilePath)
            valgrindParameters.add(programPath)
            cmdLine.parametersList.prependAll(*ArrayUtil.toStringArray(valgrindParameters))
            toolchain.patchCommandLine(cmdLine)
            putUserData<File>(OUTPUT_FILE_PATH_KEY, outputFile, configuration, context)
        } catch (e: IOException) {
            throw ExecutionException(e)
        }
    }

    override fun patchCommandLineState(
        configuration: CargoCommandConfiguration,
        environment: ExecutionEnvironment,
        state: CommandLineState,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in VALGRIND_RUNNER_IDS) return
        val project = configuration.project
        val treeDataModel = MemoryProfileTreeDataModel("Valgrind", project)
        val outputPanel = MemoryProfileOutputPanel(
            treeDataModel,
            EditValgrindSettingsAction(),
            ValgrindUtil.TREE_POPUP_ID,
            project
        )
        putUserData(DATA_MODEL_KEY, treeDataModel, configuration, context)
        putUserData(OUTPUT_PANEL_KEY, outputPanel, configuration, context)
        val console = state.consoleBuilder.console
        state.consoleBuilder = object : TextConsoleBuilderImpl(
            project,
            ExecutionSearchScopes.executionScope(environment.project, environment.runProfile)
        ) {
            override fun getConsole(): ConsoleView {
                val icon = ValgrindExecutor.getExecutorInstance().icon
                return MemoryProfileConsoleViewWrapper(ValgrindUtil.PROFILER_NAME, console, outputPanel, project, icon)
            }
        }
    }

    override fun attachToProcess(
        configuration: CargoCommandConfiguration,
        handler: ProcessHandler,
        environment: ExecutionEnvironment,
        context: ConfigurationExtensionContext
    ) {
        if (environment.runner.runnerId !in VALGRIND_RUNNER_IDS) return
        val toolchain = configuration.clean().ok?.toolchain ?: return

        val outputFile = getUserData<File>(OUTPUT_FILE_PATH_KEY, configuration, context) ?: return
        val treeDataModel = getUserData<MemoryProfileTreeDataModel>(DATA_MODEL_KEY, configuration, context) ?: return
        val outputPanel = getUserData<MemoryProfileOutputPanel>(OUTPUT_PANEL_KEY, configuration, context) ?: return
        try {
            val valgrindHandler = ValgrindHandler(treeDataModel, CidrToolEnvironment())
            val outputFileConsumer = ValgrindOutputConsumer(valgrindHandler, null)
            val accumulator = MemoryProfileStringAccumulator()
            val compositeConsumer = MemoryProfileCompositeConsumer(outputFileConsumer, accumulator)
            val consumerReplacer = Consumer<String> { string ->
                val replaced = string.replace(TAG_RE) {
                    val (tag, value) = it.destructured
                    "<$tag>${toolchain.toLocalPath(value)}</$tag>"
                }
                compositeConsumer.consume(replaced)
            }
            val fileReader = MemoryProfileFileReader(outputFile, consumerReplacer, ValgrindUtil.PROFILER_NAME)
            handler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    try {
                        fileReader.stop()
                        Disposer.dispose(compositeConsumer)
                        deleteFile(outputFile)
                    } finally {
                        fileReader.close()
                    }
                }
            })
            configureUIListeners(outputPanel, handler, accumulator, configuration.project)
        } catch (e: IOException) {
            LOG.warn("Exception during processListener setup: $e")
        }
    }

    private fun configureUIListeners(
        outputPanel: MemoryProfileOutputPanel,
        handler: ProcessHandler,
        accumulator: MemoryProfileStringAccumulator,
        project: Project
    ) {
        val application = ApplicationManager.getApplication()
        val expiredCondition = { _: Any? -> !project.isOpen || project.isDisposed }
        val tree = outputPanel.tree
        handler.addProcessListener(object : ProcessAdapter() {
            override fun startNotified(event: ProcessEvent) {
                application.invokeLater({
                    tree.setPaintBusy(true)
                    tree.emptyText.text = CLionProfilingBundle.message("valgrind.progress")
                }, expiredCondition)
            }

            override fun processTerminated(event: ProcessEvent) {
                application.invokeLater({
                    tree.setPaintBusy(false)
                    tree.emptyText.text = StatusText.getDefaultEmptyText()
                    outputPanel.setExportContent(accumulator.toString())
                }, expiredCondition)
            }
        })
    }

    private fun deleteFile(file: File) {
        if (!isUnitTestMode && !isInternal) {
            FileUtil.delete(file)
        }
    }

    private fun <T> putUserData(
        key: Key<T>,
        value: T,
        configuration: CargoCommandConfiguration,
        context: ConfigurationExtensionContext
    ) {
        if (configuration.getUserData(STORE_DATA_IN_RUN_CONFIGURATION) == true) {
            configuration.putUserData(key, value)
        } else {
            context.putUserData(key, value)
        }
    }

    private fun <T> getUserData(
        key: Key<T>,
        configuration: CargoCommandConfiguration,
        context: ConfigurationExtensionContext
    ): T? = if (configuration.getUserData(STORE_DATA_IN_RUN_CONFIGURATION) == true) {
        configuration.getUserData(key)
    } else {
        context.getUserData(key)
    }

    companion object {
        private val VALGRIND_RUNNER_IDS = listOf(RsValgrindRunner.RUNNER_ID, RsValgrindRunnerLegacy.RUNNER_ID)

        val OUTPUT_FILE_PATH_KEY = Key.create<File>("valgrind.output_file_path_key")
        val DATA_MODEL_KEY = Key.create<MemoryProfileTreeDataModel>("valgrind.data_model_key")
        val OUTPUT_PANEL_KEY = Key.create<MemoryProfileOutputPanel>("valgrind.output_panel_key")

        val STORE_DATA_IN_RUN_CONFIGURATION = Key.create<Boolean>("valgrind.store_data_in_run_configuration")

        private val TAG_RE: Regex = "<(exe|obj|dir)>(.+)</\\1>".toRegex()

        fun isEnabledFor(configuration: CargoCommandConfiguration): Boolean {
            val toolchain = configuration.clean().ok?.toolchain
            return SystemInfo.isLinux || SystemInfo.isMac || SystemInfo.isWindows && toolchain is RsWslToolchain
        }
    }
}
