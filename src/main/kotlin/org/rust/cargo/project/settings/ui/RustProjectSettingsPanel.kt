/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.settings.ui

import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.wsl.WslDistributionManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.ui.components.Link
import com.intellij.ui.layout.LayoutBuilder
import org.rust.RsBundle
import org.rust.cargo.project.RsToolchainPathChoosingComboBox
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.cargo.toolchain.RsToolchainProvider
import org.rust.cargo.toolchain.flavors.RsToolchainFlavor
import org.rust.cargo.toolchain.tools.Rustup
import org.rust.cargo.toolchain.tools.rustc
import org.rust.cargo.toolchain.tools.rustup
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
        val toolchain: RsToolchainBase?,
        val explicitPathToStdlib: String?
    )

    override fun dispose() {
        Disposer.dispose(pathToToolchainComboBox)
    }

    private val versionUpdateDebouncer = UiDebouncer(this)

    private val pathToToolchainComboBox = RsToolchainPathChoosingComboBox { update() }

    private val pathToStdlibField = pathToDirectoryTextField(this,
        RsBundle.message("settings.rust.toolchain.select.standard.library.dialog.title"))

    private var fetchedSysroot: String? = null

    private val downloadStdlibLink = Link(RsBundle.message("settings.rust.toolchain.download.rustup.link")) {
        val homePath = pathToToolchainComboBox.selectedPath ?: return@Link
        val rustup = RsToolchainProvider.getToolchain(homePath)?.rustup ?: return@Link
        object : Task.Modal(null, RsBundle.message("settings.rust.toolchain.download.rustup.dialog.title"), true) {
            override fun onSuccess() = update()

            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = RsBundle.message("settings.rust.toolchain.download.rustup.progress.text")

                rustup.downloadStdlib(this@RustProjectSettingsPanel, listener = object : ProcessAdapter() {
                    override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                        indicator.text2 = event.text.trim()
                    }
                })
            }
        }.queue()
    }.apply { isVisible = false }

    private val toolchainVersion = JLabel()

    var data: Data
        get() {
            val toolchain = pathToToolchainComboBox.selectedPath?.let { RsToolchainProvider.getToolchain(it) }
            return Data(
                toolchain = toolchain,
                explicitPathToStdlib = pathToStdlibField.text.blankToNull()
                    ?.takeIf { toolchain?.rustup == null && it != fetchedSysroot }
            )
        }
        set(value) {
            // https://youtrack.jetbrains.com/issue/KT-16367
            pathToToolchainComboBox.selectedPath = value.toolchain?.location
            pathToStdlibField.text = value.explicitPathToStdlib ?: ""
            update()
        }

    fun attachTo(layout: LayoutBuilder) = with(layout) {
        data = Data(
            toolchain = ProjectManager.getInstance().defaultProject
                // Don't use `Project.toolchain` or `Project.rustSettings` here because
                // `getService` can return `null` for default project after dynamic plugin loading.
                // As a result, you can get `java.lang.IllegalStateException`
                // So let's handle it manually
                .getService(RustProjectSettingsService::class.java)
                ?.toolchain
                ?: RsToolchainBase.suggest(cargoProjectDir),
            explicitPathToStdlib = null
        )

        row(RsBundle.message("settings.rust.toolchain.location.label")) { wrapComponent(pathToToolchainComboBox)(growX, pushX) }
        row(RsBundle.message("settings.rust.toolchain.version.label")) { toolchainVersion() }
        row(RsBundle.message("settings.rust.toolchain.standard.library.label")) { wrapComponent(pathToStdlibField)(growX, pushX) }
        row("") { downloadStdlibLink() }

        pathToToolchainComboBox.addToolchainsAsync {
            RsToolchainFlavor.getApplicableFlavors().flatMap { it.suggestHomePaths() }.distinct()
        }
    }

    @Throws(ConfigurationException::class)
    fun validateSettings() {
        val toolchain = data.toolchain ?: return
        if (!toolchain.looksLikeValidToolchain()) {
            throw ConfigurationException(RsBundle.message("settings.rust.toolchain.invalid.toolchain.error", toolchain.location))
        }
    }

    private fun update() {
        val pathToToolchain = pathToToolchainComboBox.selectedPath
        versionUpdateDebouncer.run(
            onPooledThread = {
                val toolchain = pathToToolchain?.let { RsToolchainProvider.getToolchain(it) }
                val rustc = toolchain?.rustc()
                val rustup = toolchain?.rustup
                val rustcVersion = rustc?.queryVersion()?.semver
                val stdlibLocation = rustc?.getStdlibFromSysroot(cargoProjectDir)?.presentableUrl
                Triple(rustcVersion, stdlibLocation, rustup != null)
            },
            onUiThread = { (rustcVersion, stdlibLocation, hasRustup) ->
                downloadStdlibLink.isVisible = hasRustup && stdlibLocation == null

                pathToStdlibField.isEditable = !hasRustup
                pathToStdlibField.setButtonEnabled(!hasRustup)
                if (stdlibLocation != null && (pathToStdlibField.text.isBlank() || hasRustup) ||
                    !isStdlibLocationCompatible(pathToToolchain?.toString().orEmpty(), pathToStdlibField.text)) {
                    pathToStdlibField.text = stdlibLocation.orEmpty()
                }
                fetchedSysroot = stdlibLocation

                if (rustcVersion == null) {
                    toolchainVersion.text = RsBundle.message("settings.rust.toolchain.not.applicable.version.text")
                    toolchainVersion.foreground = JBColor.RED
                } else {
                    toolchainVersion.text = rustcVersion.parsedVersion
                    toolchainVersion.foreground = JBColor.foreground()
                }
                updateListener?.invoke()
            }
        )
    }

    private val RsToolchainBase.rustup: Rustup? get() = rustup(cargoProjectDir)
}

private fun isStdlibLocationCompatible(toolchainLocation: String, stdlibLocation: String): Boolean {
    val isWslToolchain = WslDistributionManager.isWslPath(toolchainLocation)
    val isWslStdlib = WslDistributionManager.isWslPath(stdlibLocation)
    // We should reset [pathToStdlibField] because non-WSL stdlib paths don't work with WSL toolchains
    return isWslToolchain == isWslStdlib
}

private fun String.blankToNull(): String? = ifBlank { null }

private fun wrapComponent(component: JComponent): JComponent =
    JPanel(BorderLayout()).apply {
        add(component, BorderLayout.NORTH)
    }
