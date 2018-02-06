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
import org.rust.cargo.toolchain.RustToolchain
import org.rust.cargo.toolchain.Rustup
import org.rust.ide.ui.RsLayoutBuilder
import org.rust.openapiext.UiDebouncer
import org.rust.openapiext.pathToDirectoryTextField
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JLabel

class RustProjectSettingsPanel(
    private val cargoProjectDir: Path = Paths.get("."),
    private val updateListener: (() -> Unit)? = null
) : Disposable {
    data class Data(
        val toolchain: RustToolchain?,
        val explicitPathToRustSource: String?
    )

    override fun dispose() {}

    private val versionUpdateDebouncer = UiDebouncer(this)

    private val pathToToolchainField = pathToDirectoryTextField(this,
        "Select directory with cargo binary") { update() }

    private val pathToRustSourceField = pathToDirectoryTextField(this,
        "Select directory with Rust source code")

    private val downloadRustSourceLink = Link("Download via rustup", action = {
        val rustup = RustToolchain(Paths.get(pathToToolchainField.text)).rustup
        if (rustup != null) {
            object : Task.Backgroundable(null, "Downloading Rust source code") {
                override fun shouldStartInBackground(): Boolean = false
                override fun onSuccess() = update()

                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    rustup.downloadRustSource()
                }
            }.queue()
        }
    }).apply { isVisible = false }

    private val toolchainVersion = JLabel()

    var data: Data
        get() {
            val toolchain = RustToolchain(Paths.get(pathToToolchainField.text))
            return Data(
                toolchain = toolchain,
                explicitPathToRustSource = pathToRustSourceField.text.blankToNull()?.takeIf { toolchain.rustup == null }
            )
        }
        set(value) {
            // https://youtrack.jetbrains.com/issue/KT-16367
            pathToToolchainField.setText(value.toolchain?.location?.toString())
            pathToRustSourceField.text = value.explicitPathToRustSource ?: ""
            update()
        }

    fun attachTo(layout: RsLayoutBuilder) = with(layout) {
        data = Data(
            toolchain = RustToolchain.suggest(),
            explicitPathToRustSource = null
        )

        row("Toolchain location:", pathToToolchainField)
        row("Toolchain version:", toolchainVersion)
        row("Rust source location:", pathToRustSourceField)
        row(component = downloadRustSourceLink)
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
                val toolchain = RustToolchain(Paths.get(pathToToolchain))
                val rustcVersion = toolchain.queryVersions().rustc?.semver
                val rustup = toolchain.rustup
                val rustSourceLocation = rustup?.getRustSourceFromSysroot()?.presentableUrl
                Triple(rustcVersion, rustSourceLocation, rustup != null)
            },
            onUiThread = { (rustcVersion, rustSourceLocation, hasRustup) ->
                downloadRustSourceLink.isVisible = hasRustup && rustSourceLocation == null

                pathToRustSourceField.isEditable = !hasRustup
                pathToRustSourceField.button.isEnabled = !hasRustup
                if (hasRustup) {
                    pathToRustSourceField.text = rustSourceLocation ?: ""
                }

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
