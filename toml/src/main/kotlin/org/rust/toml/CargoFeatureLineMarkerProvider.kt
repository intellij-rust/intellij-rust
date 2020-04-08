/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.model.impl.CargoProjectsServiceImpl
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageOrigin.WORKSPACE
import org.rust.cargo.toolchain.RustToolchain.Companion.CARGO_TOML
import org.rust.ide.icons.RsIcons
import org.rust.ide.icons.grayed
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlTable

class CargoFeatureLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: MutableList<PsiElement>,
        result: MutableCollection<LineMarkerInfo<PsiElement>>
    ) {
        if (!tomlPluginIsAbiCompatible()) return

        val firstElement = elements.firstOrNull() ?: return
        val file = firstElement.containingFile as? TomlFile ?: return
        if (!file.name.equals(CARGO_TOML, ignoreCase = true)) return
        val cargoProject = file.findCargoProject() as? CargoProjectImpl ?: return
        val cargoPackage = file.findCargoPackage() ?: return

        val project = firstElement.project
        val cargoProjectsService = project.cargoProjects as CargoProjectsServiceImpl
        val features = cargoPackage.featureState

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
            val oldState = cargoPackage.featureState.getOrDefault(name, FeatureState.Disabled).toBoolean()
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
                icon.grayed(),
                { "Feature `$name` is $featureState" },
                null,
                Alignment.LEFT
            )
        }
    }
}
