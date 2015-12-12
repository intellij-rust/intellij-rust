package org.rust.cargo.project.config

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import org.jetbrains.annotations.Nls
import org.rust.lang.i18n.RustBundle
import javax.swing.JComponent

class RustConfigEntry : Configurable {
    private var form: RustConfigForm? = null
    private val configService = ServiceManager.getService(RustConfigService::class.java)
    private val config = configService.state

    @Nls
    override fun getDisplayName(): String {
        return RustBundle.message("config.name")
    }

    override fun getHelpTopic(): String? {
        return RustBundle.message("config.help-topic")
    }

    override fun createComponent(): JComponent? {
        form = RustConfigForm()
		return form
    }

    override fun isModified(): Boolean {
        return config.cargoBinary != form!!.cargoBinary
    }

    @Throws(ConfigurationException::class)
    override fun apply() {
        config.cargoBinary = form!!.cargoBinary
    }

    override fun reset() {
        form!!.cargoBinary = config.cargoBinary
    }

    override fun disposeUIResources() {
        form = null
    }
}
