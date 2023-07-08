/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.markup.GutterIconRenderer.Alignment
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.psi.PsiElement
import com.intellij.ui.awt.RelativePoint
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.project.workspace.PackageOrigin.WORKSPACE
import org.rust.ide.experiments.RsExperiments
import org.rust.ide.icons.RsIcons
import org.rust.ide.lineMarkers.RsLineMarkerInfoUtils
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.rust.openapiext.document
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.saveAllDocuments
import org.toml.lang.psi.*
import java.awt.event.MouseEvent

class CargoFeatureLineMarkerProvider : LineMarkerProvider {
    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? = null

    override fun collectSlowLineMarkers(
        elements: List<PsiElement>,
        result: MutableCollection<in LineMarkerInfo<*>>
    ) {
        if (!tomlPluginIsAbiCompatible()) return

        val firstElement = elements.firstOrNull() ?: return
        val file = firstElement.containingFile as? TomlFile ?: return
        if (!file.name.equals(CargoConstants.MANIFEST_FILE, ignoreCase = true)) return
        val cargoPackage = file.findCargoPackage() ?: return
        val features = cargoPackage.featureState

        loop@ for (element in elements) {
            val parent = element.parent
            if (parent is TomlKeySegment) {
                val isFeatureKey = parent.isFeatureKey
                if (!isFeatureKey && !parent.isDependencyName) continue@loop
                val featureName = parent.name ?: continue@loop
                if (!isFeatureKey && featureName !in features) continue@loop
                result += genFeatureLineMarkerInfo(
                    parent,
                    featureName,
                    features[featureName],
                    cargoPackage
                )
            }
            if (isFeatureEnabled(RsExperiments.CARGO_FEATURES_SETTINGS_GUTTER)
                && element.elementType == TomlElementTypes.L_BRACKET && cargoPackage.origin == WORKSPACE) {
                val header = parent as? TomlTableHeader ?: continue@loop
                if (!header.isFeatureListHeader) continue@loop
                result += genSettingsLineMarkerInfo(header)
            }
        }
    }

    private val PsiElement.isDependencyName
        get() = CargoTomlPsiPattern.onDependencyKey.accepts(this) ||
            CargoTomlPsiPattern.onSpecificDependencyHeaderKey.accepts(this)

    private val TomlKeySegment.isFeatureKey: Boolean
        get() {
            val keyValue = parent?.parent as? TomlKeyValue ?: return false
            val table = keyValue.parent as? TomlTable ?: return false
            return table.header.isFeatureListHeader
        }

    private fun genFeatureLineMarkerInfo(
        element: TomlKeySegment,
        name: String,
        featureState: FeatureState?,
        cargoPackage: CargoWorkspace.Package
    ): LineMarkerInfo<PsiElement> {
        val anchor = element.firstChild

        return when (cargoPackage.origin) {
            WORKSPACE -> {
                val icon = when (featureState) {
                    FeatureState.Enabled -> RsIcons.FEATURE_CHECKED_MARK
                    FeatureState.Disabled, null -> RsIcons.FEATURE_UNCHECKED_MARK
                }
                RsLineMarkerInfoUtils.create(
                    anchor,
                    anchor.textRange,
                    icon,
                    ToggleFeatureAction,
                    Alignment.RIGHT
                ) { "Toggle feature `$name`" }
            }

            else -> {
                val icon = when (featureState) {
                    FeatureState.Enabled -> RsIcons.FEATURE_CHECKED_MARK_GRAYED
                    FeatureState.Disabled, null -> RsIcons.FEATURE_UNCHECKED_MARK_GRAYED
                }
                RsLineMarkerInfoUtils.create(
                    anchor,
                    anchor.textRange,
                    icon,
                    null,
                    Alignment.RIGHT
                ) { "Feature `$name` is $featureState" }
            }
        }
    }

    private fun genSettingsLineMarkerInfo(header: TomlTableHeader): LineMarkerInfo<PsiElement> {
        val anchor = header.firstChild

        return RsLineMarkerInfoUtils.create(
            anchor,
            anchor.textRange,
            RsIcons.FEATURES_SETTINGS,
            OpenSettingsAction,
            Alignment.RIGHT
        ) { "Configure features" }
    }
}

private object ToggleFeatureAction : GutterIconNavigationHandler<PsiElement> {
    override fun navigate(e: MouseEvent, element: PsiElement) {
        val context = getContext(element) ?: return
        val featureName = element.ancestorStrict<TomlKeySegment>()?.name ?: return
        val oldState = context.cargoPackage.featureState.getOrDefault(featureName, FeatureState.Disabled)
        val newState = !oldState
        val tomlDoc = element.containingFile.document
        val isDocUnsaved = tomlDoc != null && FileDocumentManager.getInstance().isDocumentUnsaved(tomlDoc)

        if (isDocUnsaved) {
            runWriteAction { saveAllDocuments() }
            context.cargoProjectsService.refreshAllProjects()
        }

        context.cargoProjectsService.modifyFeatures(
            context.cargoProject,
            setOf(PackageFeature(context.cargoPackage, featureName)),
            newState
        )
    }
}

private object OpenSettingsAction : GutterIconNavigationHandler<PsiElement> {
    override fun navigate(e: MouseEvent, element: PsiElement) {
        val context = getContext(element) ?: return
        val dataContext = DataManager.getInstance().getDataContext(e.component)

        createActionGroupPopup(context, dataContext).show(RelativePoint(e))
    }

    private fun createActionGroupPopup(context: Context, dataContext: DataContext): JBPopup {
        val actions = listOf(
            FeaturesSettingsCheckboxAction(context, FeatureState.Enabled),
            FeaturesSettingsCheckboxAction(context, FeatureState.Disabled)
        )
        val group = DefaultActionGroup(actions)
        return JBPopupFactory.getInstance()
            .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
    }

    private class FeaturesSettingsCheckboxAction(
        private val context: Context,
        private val newState: FeatureState
    ) : AnAction() {

        init {
            val text = if (newState.isEnabled) RsBundle.message("action.enable.text") else RsBundle.message("disable")
            templatePresentation.description = RsBundle.message("action.all.features.description", text)
            templatePresentation.text = RsBundle.message("action.all.features.text", text)
        }

        override fun actionPerformed(e: AnActionEvent) {
            context.cargoProjectsService.modifyFeatures(context.cargoProject, context.cargoPackage.features, newState)
        }
    }
}

private data class Context(
    val cargoProjectsService: CargoProjectsService,
    val cargoProject: CargoProject,
    val cargoPackage: CargoWorkspace.Package
)

private fun getContext(element: PsiElement): Context? {
    val file = element.containingFile as? TomlFile ?: return null
    if (!file.name.equals(CargoConstants.MANIFEST_FILE, ignoreCase = true)) return null

    val cargoProject = file.findCargoProject() ?: return null
    val cargoPackage = file.findCargoPackage() ?: return null
    return Context(file.project.cargoProjects, cargoProject, cargoPackage)
}
