/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.vdurmont.semver4j.Semver
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CrateVersionRequirement
import org.rust.toml.stringValue
import org.toml.lang.psi.TomlValue

class NewCrateVersionAvailableInspection : CrateVersionInspection() {
    override fun handleCrateVersion(
        dependency: DependencyCrate,
        crate: CargoRegistryCrate,
        versionElement: TomlValue,
        holder: ProblemsHolder
    ) {
        val versionText = versionElement.stringValue ?: return
        val versionReq = CrateVersionRequirement.build(versionText) ?: return
        if (versionReq.isPinned) return

        val versions = crate.versions
            .filter { !it.isYanked }
            .mapNotNull { it.semanticVersion }
            .sortedDescending()

        val highestMatchingVersion = versions.firstOrNull { versionReq.matches(it) } ?: return
        val newerVersion = versions.firstOrNull { it.isRustStable && it > highestMatchingVersion } ?: return

        holder.registerProblem(
            versionElement,
            "A newer version is available for crate ${dependency.crateName}: ${newerVersion.value}",
            ProblemHighlightType.WEAK_WARNING,
            UpdateCrateVersionFix(versionElement, newerVersion.value)
        )
    }
}

/**
 * A lot of Rust crates stay at 0.x.y for a long time, so we consider even major versions 0 to be stable.
 */
private val Semver.isRustStable: Boolean
    get() = suffixTokens.isEmpty()
