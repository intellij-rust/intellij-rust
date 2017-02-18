package org.rust.cargo.runconfig.command

import com.intellij.execution.Executor
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunConfigurationWithSuppressedDefaultDebugAction
import com.intellij.execution.configurations.RunProfileState
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import org.rust.cargo.CargoConstants
import org.rust.cargo.runconfig.CargoConfigurationBase
import org.rust.cargo.runconfig.CargoRunState
import org.rust.cargo.runconfig.ui.CargoRunConfigurationEditorForm
import org.rust.cargo.toolchain.CargoCommandLine
import com.intellij.util.xmlb.annotations.Transient as XmlbTransient

class CargoCommandConfiguration(
    project: Project,
    name: String,
    factory: ConfigurationFactory
) : CargoConfigurationBase(project, name, factory),
    RunConfigurationWithSuppressedDefaultDebugAction {

    @get: XmlbTransient
    @set: XmlbTransient
    var cargoCommandLine: CargoCommandLine
        get() = _cargoArgs.toCargoCommandLine()
        set(value) {
            _cargoArgs = SerializableCargoCommandLine(value)
        }

    @Property(surroundWithTag = false)
    private var _cargoArgs = SerializableCargoCommandLine()

    init {
        cargoCommandLine = CargoCommandLine(CargoConstants.Commands.RUN)
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> =
        CargoRunConfigurationEditorForm()

    override fun getState(
        executor: Executor,
        environment: ExecutionEnvironment,
        config: ConfigurationResult
    ): RunProfileState? =
        CargoRunState(
            environment,
            config.toolchain,
            config.module,
            config.cargoProjectDirectory,
            cargoCommandLine
        )

    @Tag(value = "parameters")
    data class SerializableCargoCommandLine(
        var command: String = "",
        var additionalArguments: List<String> = mutableListOf(),
        var printBacktrace: Boolean = true,
        var environmentVariables: Map<String, String> = mutableMapOf()
    ) {
        constructor(ccl: CargoCommandLine) : this(
            ccl.command,
            ccl.additionalArguments,
            ccl.printBacktrace,
            ccl.environmentVariables
        )

        fun toCargoCommandLine(): CargoCommandLine = CargoCommandLine(
            command,
            additionalArguments,
            printBacktrace,
            environmentVariables
        )
    }
}
