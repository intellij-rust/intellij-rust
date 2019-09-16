/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests.cargo.runconfig

import com.intellij.execution.ExecutionResult
import com.intellij.execution.ExecutorRegistry
import com.intellij.execution.Location
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.ide.DataManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.runconfig.CargoCommandRunner
import org.rust.cargo.runconfig.buildtool.CargoBuildConfiguration
import org.rust.cargo.runconfig.buildtool.CargoBuildManager
import org.rust.cargo.runconfig.buildtool.CargoBuildResult
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.cargo.runconfig.command.CargoCommandConfigurationType
import org.rust.cargo.runconfig.command.CargoExecutableRunConfigurationProducer
import org.rust.cargo.runconfig.test.CargoTestRunConfigurationProducer

abstract class RunConfigurationTestBase : RsWithToolchainTestBase() {
    protected fun createConfiguration(): CargoCommandConfiguration {
        val configurationType = CargoCommandConfigurationType.getInstance()
        val factory = configurationType.factory
        return factory.createTemplateConfiguration(myModule.project) as CargoCommandConfiguration
    }

    protected fun createExecutableRunConfigurationFromContext(
        location: Location<PsiElement>? = null
    ): CargoCommandConfiguration = createRunConfigurationFromContext(CargoExecutableRunConfigurationProducer(), location)

    protected fun createTestRunConfigurationFromContext(
        location: Location<PsiElement>? = null
    ): CargoCommandConfiguration = createRunConfigurationFromContext(CargoTestRunConfigurationProducer(), location)

    private fun createRunConfigurationFromContext(
        producer: RunConfigurationProducer<CargoCommandConfiguration>,
        location: Location<PsiElement>? = null
    ): CargoCommandConfiguration = createRunnerAndConfigurationSettingsFromContext(producer, location)
        .configuration as? CargoCommandConfiguration
        ?: error("Can't create run configuration")

    protected fun createRunnerAndConfigurationSettingsFromContext(
        producer: RunConfigurationProducer<CargoCommandConfiguration>,
        location: Location<PsiElement>? = null
    ): RunnerAndConfigurationSettings {
        val context = if (location != null) {
            ConfigurationContext.createEmptyContextForLocation(location)
        } else {
            val dataContext = DataManager.getInstance().getDataContext(myFixture.editor.component)
            ConfigurationContext.getFromContext(dataContext)
        }
        return producer.createConfigurationFromContext(context)
            ?.configurationSettings
            ?: error("Can't create run configuration settings")
    }

    protected fun execute(configuration: RunConfiguration): ExecutionResult {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val state = ExecutionEnvironmentBuilder
            .create(executor, configuration)
            .build()
            .state!!
        return state.execute(executor, CargoCommandRunner())!!
    }

    protected fun executeAndGetOutput(configuration: RunConfiguration): ProcessOutput {
        val result = execute(configuration)
        val listener = AnsiAwareCapturingProcessAdapter()
        with(result.processHandler) {
            addProcessListener(listener)
            startNotify()
            waitFor()
        }
        Disposer.dispose(result.executionConsole)
        return listener.output
    }

    protected fun buildProject(): CargoBuildResult {
        val buildConfiguration = createBuildConfiguration()
        return CargoBuildManager.build(buildConfiguration).get()
    }

    protected fun createBuildConfiguration(): CargoBuildConfiguration {
        val executor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID)
        val runner = ProgramRunner.findRunnerById(CargoCommandRunner.RUNNER_ID)!!
        val runManager = RunManager.getInstance(project) as RunManagerImpl
        val configuration = CargoBuildManager.getBuildConfiguration(createConfiguration())!!
        val settings = RunnerAndConfigurationSettingsImpl(runManager, configuration)
        val environment = ExecutionEnvironment(executor, runner, settings, project)
        return CargoBuildConfiguration(configuration, environment)
    }
}

/**
 * Capturing adapter that removes ANSI escape codes from the output
 */
class AnsiAwareCapturingProcessAdapter : ProcessAdapter(), AnsiEscapeDecoder.ColoredTextAcceptor {
    val output = ProcessOutput()

    private val decoder = object : AnsiEscapeDecoder() {
        override fun getCurrentOutputAttributes(outputType: Key<*>) = outputType
    }

    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) =
        decoder.escapeText(event.text, outputType, this)

    private fun addToOutput(text: String, outputType: Key<*>) {
        if (outputType === ProcessOutputTypes.STDERR) {
            output.appendStderr(text)
        } else {
            output.appendStdout(text)
        }
    }

    override fun processTerminated(event: ProcessEvent) {
        output.exitCode = event.exitCode
    }

    override fun coloredTextAvailable(text: String, attributes: Key<*>) =
        addToOutput(text, attributes)
}
