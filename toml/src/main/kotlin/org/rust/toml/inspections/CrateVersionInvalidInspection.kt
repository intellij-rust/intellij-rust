/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.toml.crates.local.CrateVersionRequirement
import org.rust.toml.crates.local.CratesLocalIndexException
import org.rust.toml.crates.local.CratesLocalIndexService
import org.rust.toml.stringValue
import org.toml.lang.psi.TomlVisitor

class CrateVersionInvalidInspection : CargoTomlInspectionToolBase() {
    override val requiresLocalCrateIndex: Boolean = true

    override fun buildCargoTomlVisitor(holder: ProblemsHolder): TomlVisitor {
        return object : CargoDependencyCrateVisitor() {
            override fun visitDependency(dependency: DependencyCrate) {
                if (dependency.isForeign()) return

                val crate = try {
                    CratesLocalIndexService.getInstance().getCrate(dependency.crateName) ?: return
                } catch (e: CratesLocalIndexException) {
                    return
                }

                val versionElement = dependency.properties["version"] ?: return
                val versionText = versionElement.stringValue ?: return

                val versionReq = CrateVersionRequirement.build(versionText)
                if (versionReq == null) {
                    holder.registerProblem(
                        versionElement,
                        "Invalid version requirement $versionText",
                        ProblemHighlightType.WEAK_WARNING
                    )
                    return
                }

                val compatibleVersions = crate.versions.filter {
                    val version = it.semanticVersion ?: return@filter false
                    versionReq.matches(version)
                }

                if (compatibleVersions.isEmpty()) {
                    holder.registerProblem(versionElement, "No version matching $versionText found for crate ${dependency.crateName}")
                } else if (compatibleVersions.all { it.isYanked }) {
                    holder.registerProblem(versionElement, "All versions matching $versionText for crate ${dependency.crateName} are yanked")
                }
            }
        }
    }
}
