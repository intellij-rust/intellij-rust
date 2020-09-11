/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.ui.JBColor
import com.intellij.ui.components.Link
import com.intellij.ui.layout.LayoutBuilder
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.pathToDirectoryTextField
import java.awt.BorderLayout
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class RustProjectSettingsPanel(
    private val cargoProjectDir: Path = Paths.get("."),
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    data class Data(
        val toolchain: RustToolchain?,
        val explicitPathToStdlib: String?
    )

    override fun dispose() {}

    private val versionUpdateDebouncer = UiDebouncer(this)

    private val pathToToolchainField = pathToDirectoryTextField(this,
        "Select directory with cargo binary") { update() }

    private val pathToStdlibField = pathToDirectoryTextField(this,
        "Select directory with standard library source code")

    private var fetchedSysroot: String? = null

    private val downloadStdlibLink = Link("Download via rustup", action = {
        val rustup = RustToolchain.get(Paths.get(pathToToolchainField.text)).rustup
        if (rustup != null) {
            object : Task.Backgroundable(null, "Downloading Rust standard library") {
                override fun shouldStartInBackground(): Boolean = false
                override fun onSuccess() = update()

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    rustup.downloadStdlib()
                }
            }.queue()
        }
    }).apply { isVisible = false }

    private val toolchainVersion = JLabel()

    var data: Data
        get() {
            if (pathToToolchainField.text.isEmpty()) {
                return Data(null, null)
            }
            val toolchain = RustToolchain.get(Paths.get(pathToToolchainField.text))
            return Data(
                toolchain = toolchain,
                explicitPathToStdlib = pathToStdlibField.text.blankToNull()
                    ?.takeIf { toolchain.rustup == null && it != fetchedSysroot }
            )
        }
        set(value) {
            // https://youtrack.jetbrains.com/issue/KT-16367
            pathToToolchainField.setText(value.toolchain?.location?.toString())
            pathToStdlibField.text = value.explicitPathToStdlib ?: ""
            update()
        }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        data = Data(
            toolchain = RustToolchain.suggest(),
            explicitPathToStdlib = null
        )

        row("Toolchain location:") { wrapComponent(pathToToolchainField)(growX, pushX) }
        row("Toolchain version:") { toolchainVersion() }
        row("Standard library:") { wrapComponent(pathToStdlibField)(growX, pushX) }
        row("") { downloadStdlibLink() }
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
                if (pathToToolchain.isEmpty()) {
                    return@run Triple(null, null, false)
                }
                val toolchain = RustToolchain.get(Paths.get(pathToToolchain))
                val rustcVersion = toolchain.queryVersions().rustc?.semver
                val rustup = toolchain.rustup
                val stdlibLocation = toolchain.getStdlibFromSysroot(cargoProjectDir)?.presentableUrl
                Triple(rustcVersion, stdlibLocation, rustup != null)
            },
            onUiThread = { (rustcVersion, stdlibLocation, hasRustup) ->
                downloadStdlibLink.isVisible = hasRustup && stdlibLocation == null

                pathToStdlibField.isEditable = !hasRustup
                pathToStdlibField.button.isEnabled = !hasRustup
                if (stdlibLocation != null && (pathToStdlibField.text.isBlank() || hasRustup)) {
                    pathToStdlibField.text = stdlibLocation
                }
                fetchedSysroot = stdlibLocation

                if (rustcVersion == null) {
                    toolchainVersion.text = "N/A"
                    toolchainVersion.foreground = JBColor.RED
                } else {
                    toolchainVersion.text = rustcVersion.parsedVersion
                    toolchainVersion.foreground = JBColor.foreground()
                }
                updateListener?.invoke()
            }
        )
    }

    private val RustToolchain.rustup: Rustup? get() = rustup(cargoProjectDir)
}

private fun String.blankToNull(): String? = if (isBlank()) null else this

private fun wrapComponent(component: JComponent): JComponent =
    JPanel(BorderLayout()).apply {
        add(component, BorderLayout.NORTH)
    }
