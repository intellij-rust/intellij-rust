package org.rust.cargo.runconfig

import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.CapturingProcessAdapter
import com.intellij.execution.process.ProcessOutput
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.util.Disposer
import org.assertj.core.api.Assertions.assertThat
import org.rust.cargo.CargoTestCaseBase

class RunConfigurationTestCase : CargoTestCaseBase() {
    override val testDataPath = "src/test/resources/org/rust/cargo/runconfig/fixtures/hello"

    fun testApplicationConfiguration() {
       val configuration = createConfiguration()
       val result = execute(configuration)

       assertThat(result.stdout).contains("Hello, world!")
    }

    private fun createConfiguration(): CargoCommandConfiguration {
        val configurationType = ConfigurationTypeUtil.findConfigurationType(CargoCommandRunConfigurationType::class.java)
        val factory = configurationType.configurationFactories[0]
        val configuration = factory.createTemplateConfiguration(myProject) as CargoCommandConfiguration
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

        val listener = CapturingProcessAdapter()
        with(result.processHandler) {
            addProcessListener(listener)
            startNotify()
            waitFor()
        }
        Disposer.dispose(result.executionConsole)
        return listener.output
    }
}
