package org.rust.cargo.toolchain

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

class RustConfigurableProvider(
    private val project: Project
) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable = RustToolchainConfigurable(project)
}
