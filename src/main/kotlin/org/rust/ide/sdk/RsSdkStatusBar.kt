/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.sdk

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.settings.rustSettings
import org.rust.ide.sdk.RsSdkPopupFactory.Companion.descriptionInPopup
import org.rust.ide.sdk.RsSdkPopupFactory.Companion.shortenNameInPopup
import org.rust.ide.sdk.RsSdkRenderingUtils.noToolchainMarker

private const val RUST_SDK_WIDGET_ID: String = "rustToolchainWidget"

class RsSdkStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = RUST_SDK_WIDGET_ID

    override fun getDisplayName(): String = "Rust Toolchain"

    override fun isAvailable(project: Project): Boolean {
        val cargoProjects = project.cargoProjects
        return cargoProjects.hasAtLeastOneValidProject
            || cargoProjects.suggestManifests().any()
    }

    override fun createWidget(project: Project): StatusBarWidget = RsSdkStatusBar(project)

    override fun disposeWidget(widget: StatusBarWidget) = Disposer.dispose(widget)

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class RsSwitchSdkAction : DumbAwareAction("Switch Rust Toolchain", null, null) {

    override fun update(e: AnActionEvent) {
        e.presentation.isVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dataContext = e.dataContext
        RsSdkPopupFactory(project).createPopup(dataContext)?.showInBestPositionFor(dataContext)
    }
}

private class RsSdkStatusBar(project: Project) : EditorBasedStatusBarPopup(project, false) {

    override fun ID(): String = RUST_SDK_WIDGET_ID

    override fun getWidgetState(file: VirtualFile?): WidgetState {
        val sdk = project.rustSettings.sdk
        return if (sdk != null) {
            WidgetState(
                "\"Current Toolchain: ${descriptionInPopup(sdk)}\"",
                shortenNameInPopup(sdk, 50),
                true
            )
        } else {
            WidgetState("", noToolchainMarker, true)
        }
    }

    override fun isEnabledForFile(file: VirtualFile?): Boolean = true

    override fun createPopup(context: DataContext): ListPopup? = RsSdkPopupFactory(project).createPopup(context)

    override fun registerCustomListeners() {
        project.messageBus.connect(this).apply {
            subscribe(RustProjectSettingsService.RUST_SETTINGS_TOPIC,
                object : RustProjectSettingsService.RustSettingsListener {
                    override fun rustSettingsChanged(e: RustProjectSettingsService.RustSettingsChangedEvent) {
                        update()
                    }
                })

            subscribe(CargoProjectsService.CARGO_PROJECTS_TOPIC, object : CargoProjectsService.CargoProjectsListener {
                override fun cargoProjectsUpdated(service: CargoProjectsService, projects: Collection<CargoProject>) {
                    update()
                }
            })

            subscribe(ProjectJdkTable.JDK_TABLE_TOPIC, object : ProjectJdkTable.Listener {
                override fun jdkRemoved(jdk: Sdk) {
                    if (jdk.sdkType is RsSdkType) {
                        update()
                    }
                }

                override fun jdkAdded(jdk: Sdk) {}

                override fun jdkNameChanged(jdk: Sdk, previousName: String) {
                    if (jdk.sdkType !is RsSdkType) return
                    val projectSdk = project.rustSettings.sdk ?: return
                    if (jdk.key == projectSdk.key) {
                        update()
                    }
                }
            })

            subscribe(RsSdkAdditionalData.RUST_ADDITIONAL_DATA_TOPIC, object : RsSdkAdditionalData.Listener {
                override fun sdkAdditionalDataChanged(sdk: Sdk) {
                    if (sdk.sdkType !is RsSdkType) return
                    val projectSdk = project.rustSettings.sdk ?: return
                    if (sdk.key == projectSdk.key) {
                        update()
                    }
                }
            })
        }
    }

    override fun createInstance(project: Project): StatusBarWidget = RsSdkStatusBar(project)
}
