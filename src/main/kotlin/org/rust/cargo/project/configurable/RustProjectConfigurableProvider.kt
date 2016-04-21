package org.rust.cargo.project.configurable

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project

class RustProjectConfigurableProvider(
    private val project: Project
) : ConfigurableProvider() {

    override fun createConfigurable(): Configurable = RustProjectConfigurable(project)
}
