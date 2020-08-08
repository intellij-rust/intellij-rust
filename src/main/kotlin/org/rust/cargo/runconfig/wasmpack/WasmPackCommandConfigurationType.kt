/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.ConfigurationTypeUtil
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import org.rust.ide.icons.RsIcons

class WasmPackCommandConfigurationType : ConfigurationTypeBase(
    "WasmPackCommandRunConfiguration",
    "wasm-pack",
    "wasm-pack command run configuration",
    RsIcons.WASM_PACK
) {
    init {
        addFactory(WasmPackConfigurationFactory(this))
    }

    val factory: ConfigurationFactory get() = configurationFactories.single()

    companion object {
        fun getInstance(): WasmPackCommandConfigurationType =
            ConfigurationTypeUtil.findConfigurationType(WasmPackCommandConfigurationType::class.java)
    }
}

class WasmPackConfigurationFactory(type: WasmPackCommandConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = ID

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return WasmPackCommandConfiguration(project, "wasm-pack", this)
    }

    companion object {
        const val ID: String = "wasm-pack"
    }
}
