/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CratesLocalIndexService
import org.toml.lang.psi.TomlValue
import org.toml.lang.psi.TomlVisitor

abstract class CrateVersionInspection : CargoTomlInspectionToolBase() {
    override val requiresLocalCrateIndex: Boolean = true

    abstract fun handleCrateVersion(
        dependency: DependencyCrate,
        crate: CargoRegistryCrate,
        versionElement: TomlValue,
        holder: ProblemsHolder
    )

    override fun buildCargoTomlVisitor(holder: ProblemsHolder): TomlVisitor {
        return object : CargoDependencyCrateVisitor() {
            override fun visitDependency(dependency: DependencyCrate) {
                if (dependency.isForeign()) return

                val crate = CratesLocalIndexService.getInstance().getCrate(dependency.crateName).ok() ?: return

                val versionElement = dependency.properties["version"] ?: return
                handleCrateVersion(dependency, crate, versionElement, holder)
            }
        }
    }
}
