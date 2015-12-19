package org.rust.cargo.project.config

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import org.jetbrains.annotations.Nls
import org.rust.lang.i18n.RustBundle
import javax.swing.JComponent

class RustConfigEntry : Configurable {

    private val config = ServiceManager.getService(RustConfigService::class.java).state

    private var form: RustConfigForm? = null

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

    override fun isModified(): Boolean =
        form?.let { f -> config.cargoBinary != f.cargoBinary } ?: false

    @Throws(ConfigurationException::class)
    override fun apply() {
        form?.let { f ->
            config.cargoBinary = form!!.cargoBinary
        }
    }

    override fun reset() {
        form?.let { f ->
            f.cargoBinary = config.cargoBinary
        }
    }

    override fun disposeUIResources() {
        form = null
    }
}
