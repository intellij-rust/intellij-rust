/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.rust.cargo.CargoConstants
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.ide.fixes.EnableCargoFeaturesFix
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.ext.containingCargoTarget
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.lang.core.psi.ext.findCargoProject
import org.rust.openapiext.pathAsPath

class MissingFeaturesInspection : LocalInspectionTool() {

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
        return when {
            file is RsFile -> checkRsFile(file, manager, isOnTheFly)
            file.name == CargoConstants.MANIFEST_FILE -> checkCargoTomlFile(file, manager, isOnTheFly)
            else -> null
        }
    }

    private fun checkCargoTomlFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
        val cargoProject = file.findCargoProject() ?: return null
        val pkg = file.findCargoPackage()?.takeIf { it.rootDirectory == file.virtualFile?.parent?.pathAsPath } ?: return null
        val missingFeatures = collectMissingFeaturesForPackage(pkg)

        return createProblemDescriptors(missingFeatures, manager, file, isOnTheFly, cargoProject)
    }

    private fun checkRsFile(file: RsFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor?>? {
        val cargoProject = file.findCargoProject() ?: return null
        val target = file.containingCargoTarget ?: return null
        if (target.pkg.origin != PackageOrigin.WORKSPACE) return null
        val missingFeatures = collectMissingFeatureForTarget(target)

        return createProblemDescriptors(missingFeatures, manager, file, isOnTheFly, cargoProject)
    }

    private fun collectMissingFeatureForTarget(target: CargoWorkspace.Target): Set<PackageFeature> {
        val missingFeatures = mutableSetOf<PackageFeature>()

        collectMissingFeaturesForPackage(target.pkg, missingFeatures)

        val libTarget = target.pkg.libTarget

        if (libTarget != null && target != libTarget) {
            for (requiredFeature in target.requiredFeatures) {
                if (target.pkg.featureState[requiredFeature] == FeatureState.Disabled) {
                    missingFeatures += PackageFeature(target.pkg, requiredFeature)
                }
            }
        }
        return missingFeatures
    }

    companion object {
        private fun collectMissingFeaturesForPackage(pkg: CargoWorkspace.Package, missingFeatures: MutableSet<PackageFeature>) {
            for (dep in pkg.dependencies) {
                if (dep.pkg.origin == PackageOrigin.WORKSPACE) {
                    for (requiredFeature in dep.requiredFeatures) {
                        if (dep.pkg.featureState[requiredFeature] == FeatureState.Disabled) {
                            missingFeatures += PackageFeature(dep.pkg, requiredFeature)
                        }
                    }
                }
            }
        }

        private fun collectMissingFeaturesForPackage(pkg: CargoWorkspace.Package): Set<PackageFeature> {
            val missingFeatures = mutableSetOf<PackageFeature>()
            collectMissingFeaturesForPackage(pkg, missingFeatures)
            return missingFeatures
        }

        private fun createProblemDescriptors(
            missingFeatures: Set<PackageFeature>,
            manager: InspectionManager,
            file: PsiFile,
            isOnTheFly: Boolean,
            cargoProject: CargoProject
        ): Array<ProblemDescriptor?> {
            return if (missingFeatures.isEmpty()) {
                ProblemDescriptor.EMPTY_ARRAY
            } else {
                arrayOf(
                    manager.createProblemDescriptor(
                        file,
                        RsBundle.message("inspection.message.missing.features", missingFeatures.joinToString()),
                        isOnTheFly,
                        arrayOf(EnableCargoFeaturesFix(cargoProject, missingFeatures)),
                        ProblemHighlightType.WARNING
                    )
                )
            }
        }
    }
}
