package org.rust.cargo.project.settings.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.components.Link
import com.intellij.ui.layout.CCFlags
import com.intellij.ui.layout.LayoutBuilder
import com.intellij.util.text.SemVer
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RustToolchain
import org.rust.utils.UiDebouncer
import org.rust.utils.pathToDirectoryTextField
import javax.swing.JCheckBox
import javax.swing.JLabel

class RustProjectSettingsPanel(private val cargoProjectDir: String = ".") : Disposable {
    data class Data(
        val toolchain: RustToolchain?,
        val autoUpdateEnabled: Boolean,
        val explicitPathToStdlib: String?
    ) {
        fun applyTo(settings: RustProjectSettingsService) {
            settings.data = RustProjectSettingsService.Data(
                toolchain,
                autoUpdateEnabled,
                null
            )
        }
    }

    override fun dispose() {}

    private val versionUpdateDebouncer = UiDebouncer(this)

    private val pathToToolchainField = pathToDirectoryTextField(this,
        "Select directory with cargo binary", { update() })

    private val pathToStdlibField = pathToDirectoryTextField(this,
        "Select directory with standard library source code")
    private val dowloadStdlibLink = Link("Download via rustup") {
        val rustup = RustToolchain(pathToToolchainField.text).rustup(cargoProjectDir)
        if (rustup != null) {
            object : Task.Backgroundable(null, "Downloading Rust standard library") {
                override fun shouldStartInBackground(): Boolean = false

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    rustup.downloadStdlib()
                }

                override fun onSuccess() {
                    update()
                }
            }.queue()
        }
    }

    private val autoUpdateEnabled = JCheckBox()
    private val toolchainVersion = JLabel()

    var data: Data
        get() = Data(
            RustToolchain(pathToToolchainField.text),
            autoUpdateEnabled.isSelected,
            pathToStdlibField.text.blankToNull()
        )
        set(value) {
            // https://youtrack.jetbrains.com/issue/KT-16367
            pathToToolchainField.setText(value.toolchain?.location)
            autoUpdateEnabled.isSelected = value.autoUpdateEnabled
            pathToStdlibField.text = ""
            update()
        }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        data = Data(
            RustToolchain.suggest(),
            true,
            null
        )

        row("Toolchain location:") { pathToToolchainField(CCFlags.pushX) }
        row("Toolchain version:") { toolchainVersion() }
        row("Standard library:") { pathToStdlibField() }
        row { dowloadStdlibLink() }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException("Invalid toolchain location: can't find Cargo in ${toolchain.location}")
        }
    }

    private fun update() {
        val pathToToolchain = pathToToolchainField.text
        versionUpdateDebouncer.run(
            onPooledThread = {
                val toolchain = RustToolchain(pathToToolchain)
                val rustcVerson = toolchain.queryVersions().rustc.semver
                val rustup = toolchain.rustup(cargoProjectDir)
                val stdlibLocation = rustup?.getStdlibFromSysroot()?.presentableUrl
                Triple(rustcVerson, stdlibLocation, rustup != null)
            },
            onUiThread = { (rustcVersion, stdlibLocation, hasRustup) ->
                dowloadStdlibLink.isVisible = hasRustup
                if (rustcVersion == SemVer.UNKNOWN) {
                    toolchainVersion.text = "N/A"
                    toolchainVersion.foreground = JBColor.RED
                } else {
                    toolchainVersion.text = rustcVersion.parsedVersion
                    toolchainVersion.foreground = JBColor.foreground()
                }

                pathToStdlibField.text = stdlibLocation ?: ""
            }
        )
    }
}

private fun String.blankToNull(): String? = if (isBlank()) null else this
