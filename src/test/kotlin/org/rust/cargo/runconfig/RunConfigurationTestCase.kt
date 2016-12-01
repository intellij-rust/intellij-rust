package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.*
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.RustWithToolchainTestBase

class RunConfigurationTestCase : RustWithToolchainTestBase() {
    override val dataPath = "src/test/resources/org/rust/cargo/runconfig/fixtures"

    fun testApplicationConfiguration() = withProject("hello") {
        val configuration = createConfiguration()
        val result = execute(configuration)

        assertThat(result.stdout).contains("Hello, world!")
    }

    private fun createConfiguration(): CargoCommandConfiguration {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(CargoCommandRunConfigurationType::class.java)
        val factory = configurationType.configurationFactories[0]
        val configuration = factory.createTemplateConfiguration(myModule.project) as CargoCommandConfiguration
        configuration.setModule(myModule)
        return configuration
    }

    private fun execute(configuration: RunConfiguration): ProcessOutput {
        val executor = DefaultRunExecutor.getRunExecutorInstance()
        val state = ExecutionEnvironmentBuilder
            .create(executor, configuration)
            .build()
            .state!!

        val result = state.execute(executor, RustRunner())!!

        val listener = AnsiAwareCapturingProcessAdapter()
        with(result.processHandler) {
            addProcessListener(listener)
            startNotify()
            waitFor()
        }
        Disposer.dispose(result.executionConsole)
        return listener.output
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
