/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import org.rust.RsBundle
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CrateVersionRequirement
import org.rust.toml.stringValue
import org.toml.lang.psi.TomlValue

class CrateVersionInvalidInspection : CrateVersionInspection() {
    override fun handleCrateVersion(
        dependency: DependencyCrate,
        crate: CargoRegistryCrate,
        versionElement: TomlValue,
        holder: ProblemsHolder
    ) {
        val versionText = versionElement.stringValue ?: return

        val versionReq = CrateVersionRequirement.build(versionText)
        if (versionReq == null) {
            holder.registerProblem(
                versionElement,
                RsBundle.message("inspection.message.invalid.version.requirement", versionText),
                ProblemHighlightType.WEAK_WARNING
            )
            return
        }

        val compatibleVersions = crate.versions.filter {
            val version = it.semanticVersion ?: return@filter false
            versionReq.matches(version)
        }

        if (compatibleVersions.isEmpty()) {
            holder.registerProblem(versionElement, RsBundle.message("inspection.message.no.version.matching.found.for.crate", versionText, dependency.crateName))
        } else if (compatibleVersions.all { it.isYanked }) {
            holder.registerProblem(versionElement, RsBundle.message("inspection.message.all.versions.matching.for.crate.are.yanked", versionText, dependency.crateName))
        }
    }
}
