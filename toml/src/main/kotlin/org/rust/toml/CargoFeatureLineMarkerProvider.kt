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
import com.intellij.ui.ColorUtil
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.IconUtil
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin.WORKSPACE
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO_TOML
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.toml.lang.psi.*
import java.awt.Color
import java.awt.event.MouseEvent
import java.awt.image.RGBImageFilter

class CargoFeatureLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(elements: MutableList<PsiElement>, result: MutableCollection<LineMarkerInfo<PsiElement>>) {
        if (!tomlPluginIsAbiCompatible()) return

        val firstElement = elements.firstOrNull() ?: return
        val file = firstElement.containingFile as? TomlFile ?: return
        if (!file.name.equals(CARGO_TOML, ignoreCase = true)) return
        val cargoProject = file.findCargoProject() as? CargoProjectImpl ?: return
        val cargoPackage = file.findCargoPackage() ?: return
        val cargoPackageOrigin = cargoPackage.origin

        val project = firstElement.project
        val cargoProjectsService = project.cargoProjects as CargoProjectsServiceImpl
        val features = cargoPackage.featureStates

        loop@ for (element in elements) {
            val parent = element.parent
            if (parent is TomlKey) {
                val key = parent
                val keyValue = key.parent as? TomlKeyValue ?: continue@loop
                val table = keyValue.parent as? TomlTable ?: continue@loop
                if (!table.header.isFeatureListHeader) continue@loop
                val featureName = key.text
                if ("." in featureName) continue@loop
                result += genFeatureLineMarkerInfo(
                    key,
                    featureName,
                    features[featureName],
                    cargoProject,
                    cargoProjectsService,
                    cargoPackage
                )
            }
            if (element.elementType == TomlElementTypes.L_BRACKET && cargoPackageOrigin == WORKSPACE) {
                val header = parent as? TomlTableHeader ?: continue@loop
                if (!header.isFeatureListHeader) continue@loop
                val settingsLineMarkerInfo = genSettingsLineMarkerInfo(header, cargoProjectsService)
                result.add(settingsLineMarkerInfo)
            }
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
            FeatureState.Disabled, null -> RsIcons.FEATURE_UNCHECKED_MARK
        }

        val toggleFeature = {
            val oldState = cargoPackage.featureStates.getOrDefault(name, FeatureState.Disabled).toBoolean()
            val newState = !oldState
            val tomlFile = element.containingFile as TomlFile
            val tomlDoc = PsiDocumentManager.getInstance(cargoProject.project).getDocument(tomlFile)
            val isDocUnsaved = tomlDoc?.let { FileDocumentManager.getInstance().isDocumentUnsaved(it) } ?: true
            cargoProjectsService.updateFeature(cargoProject, cargoPackage, name, newState, isDocUnsaved)
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
                // TODO extract to a constant
                IconUtil.filterIcon(icon, { object : RGBImageFilter() {
                    override fun filterRGB(x: Int, y: Int, rgb: Int): Int {
                        val color = Color(rgb, true)
                        return ColorUtil.toAlpha(color, (color.alpha / 2.2).toInt()).rgb
                    }
                } }, null),
                { "Feature `$name` is $featureState" },
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
            // TODO а зачем тут по второму разу все проверять? Нельзя в замыкание?
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
//        cargoProjectsService.updateAllFeatures(cargoProject, pkgRootDir, selectAll)

        runWriteAction {
            DaemonCodeAnalyzer.getInstance(cargoProject.project).restart()
        }
    }
}
