/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin.WORKSPACE
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader
import java.awt.event.MouseEvent

class CargoFeatureLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        if (!tomlPluginIsAbiCompatible()) return

        for (element in elements) {
            if (element !is TomlTable) continue

            val file = element.containingFile as? TomlFile ?: continue
            if (file.name.toLowerCase() != "cargo.toml") continue
            val cargoProject = file.findCargoProject() as? CargoProjectImpl ?: continue
            val cargoPackage = file.findCargoPackage() ?: continue

            val project = element.project
            val cargoProjectsService = project.cargoProjects as CargoProjectsServiceImpl
            val features = cargoPackage.features.state

            val lastName = element.header.names.lastOrNull() ?: continue
            if (!lastName.isFeaturesKey) continue

            for (entry in element.entries) {
                val featureName = entry.key.text
                val featureLineMarkerInfo = genFeatureLineMarkerInfo(
                    entry.key,
                    featureName,
                    features[featureName],
                    cargoProject,
                    cargoProjectsService,
                    cargoPackage
                )
                result.add(featureLineMarkerInfo)
            }

            val settingsLineMarkerInfo = genSettingsLineMarkerInfo(element.header, cargoProjectsService)
            result.add(settingsLineMarkerInfo)
        }
    }

    private fun genFeatureLineMarkerInfo(
        element: TomlKey,
        name: String,
        featureState: FeatureState?,
        cargoProject: CargoProjectImpl,
        cargoProjectsService: CargoProjectsServiceImpl,
        cargoPackage: CargoWorkspace.Package
    ): LineMarkerInfo<PsiElement> {
        val anchor = element.firstChild
        val icon = when (featureState) {
            FeatureState.Enabled -> RsIcons.FEATURE_CHECKED_MARK
            FeatureState.Disabled -> RsIcons.FEATURE_UNCHECKED_MARK
            null -> RsIcons.FEATURE_UNCHECKED_MARK
        }

        val toggleFeature = {
            val oldState = cargoPackage.features.state.getOrDefault(name, FeatureState.Disabled).toBoolean()
            val newState = !oldState
            val tomlFile = element.containingFile as TomlFile
            val tomlDoc = PsiDocumentManager.getInstance(cargoProject.project).getDocument(tomlFile)
            val isDocUnsaved = tomlDoc?.let { FileDocumentManager.getInstance().isDocumentUnsaved(it) } ?: true
            val pkgRootDir = cargoPackage.rootDirectory.toString()
            cargoProjectsService.updateFeature(cargoProject, pkgRootDir, name, newState, isDocUnsaved)
        }

        return when (cargoPackage.origin) {
            WORKSPACE -> LineMarkerInfo(
                anchor,
                anchor.textRange,
                icon,
                { "Toggle feature `$name`" },
                { _, _ -> toggleFeature() },
                Alignment.LEFT
            )

            else -> LineMarkerInfo(
                anchor,
                anchor.textRange,
                icon,
                null,
                null,
                Alignment.LEFT
            )
        }
    }

    private fun genSettingsLineMarkerInfo(
        header: TomlTableHeader,
        cargoProjectsService: CargoProjectsServiceImpl
    ): LineMarkerInfo<PsiElement> {
        val anchor = header.firstChild
        val icon = RsIcons.FEATURES_SETTINGS

        val configure = { e: MouseEvent, element: PsiElement ->
            val file = element.containingFile as? TomlFile
            if (file != null && file.name.toLowerCase() == "cargo.toml") {
                val cargoProject = file.findCargoProject() as? CargoProjectImpl
                val cargoPackage = file.findCargoPackage()

                if (cargoProject != null && cargoPackage != null) {
                    val popup = createActionGroupPopup(cargoProject, cargoPackage, cargoProjectsService)
                    popup.show(RelativePoint(e))
                }
            }
        }

        return LineMarkerInfo(
            anchor,
            anchor.textRange,
            icon,
            { "Configure features" },
            { e, element -> configure(e, element) },
            Alignment.LEFT
        )
    }
}


private fun createActionGroupPopup(
    cargoProject: CargoProjectImpl,
    cargoPackage: CargoWorkspace.Package,
    cargoProjectsService: CargoProjectsServiceImpl
): JBPopup {
    val actions = listOf(
        FeaturesSettingsCheckboxAction(true, cargoProject, cargoPackage, cargoProjectsService),
        FeaturesSettingsCheckboxAction(false, cargoProject, cargoPackage, cargoProjectsService)
    )
    val group = DefaultActionGroup(actions)
    val context = SimpleDataContext.getProjectContext(null)
    return JBPopupFactory.getInstance()
        .createActionGroupPopup(null, group, context, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
}

private class FeaturesSettingsCheckboxAction(
    private val selectAll: Boolean,
    private val cargoProject: CargoProjectImpl,
    private val cargoPackage: CargoWorkspace.Package,
    private val cargoProjectsService: CargoProjectsServiceImpl
) : AnAction() {

    init {
        val text = when (selectAll) {
            true -> "Select all"
            false -> "Select none"
        }
        templatePresentation.description = text
        templatePresentation.text = text
    }

    override fun actionPerformed(e: AnActionEvent) {
        val pkgRootDir = cargoPackage.rootDirectory.toString()
        cargoProjectsService.updateAllFeatures(cargoProject, pkgRootDir, selectAll)

        runWriteAction {
            DaemonCodeAnalyzer.getInstance(cargoProject.project).restart()
        }
    }
}
