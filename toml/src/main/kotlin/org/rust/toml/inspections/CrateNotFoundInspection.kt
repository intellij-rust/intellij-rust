/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.cargo.CargoConstants
import org.rust.ide.experiments.RsExperiments
import org.rust.openapiext.isFeatureEnabled
import org.rust.toml.crates.local.CratesLocalIndexException
import org.rust.toml.crates.local.CratesLocalIndexService
import org.rust.toml.isDependencyKey
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

class CrateNotFoundInspection : TomlLocalInspectionToolBase() {
    override fun buildVisitorInternal(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor? {
        if (!isFeatureEnabled(RsExperiments.CRATES_LOCAL_INDEX)) return null
        if (holder.file.name != CargoConstants.MANIFEST_FILE) return null

        return object : TomlVisitor() {
            override fun visitKeyValue(element: TomlKeyValue) {
                val table = element.parent as? TomlTable ?: return
                val depTable = DependencyTable.fromTomlTable(table) ?: return
                if (depTable !is DependencyTable.General) return

                val segment = element.key.segments.getOrNull(0) ?: return
                val name = segment.name ?: return
                val value = element.value ?: return
                if (value is TomlLiteral && value.kind is TomlLiteralKind.String) {
                    handleDependency(DependencyCrate(name, segment, mapOf("version" to value)), holder)
                } else if (value is TomlInlineTable) {
                    handleDependency(DependencyCrate(name, segment, collectProperties(value)), holder)
                }
            }

            override fun visitTable(element: TomlTable) {
                val depTable = DependencyTable.fromTomlTable(element) ?: return
                if (depTable !is DependencyTable.Specific) return

                handleDependency(
                    DependencyCrate(depTable.crateName, depTable.crateNameElement, collectProperties(element)), holder
                )
            }
        }
    }

    private fun handleDependency(dependency: DependencyCrate, holder: ProblemsHolder) {
        // Do not check local, custom or git crates
        for (property in IGNORED_PROPERTIES) {
            if (property in dependency.properties) return
        }

        val crate = try {
            CratesLocalIndexService.getInstance().getCrate(dependency.crateName)
        } catch (e: CratesLocalIndexException) {
            return
        }

        if (crate == null) {
            holder.registerProblem(dependency.crateNameElement, "Crate ${dependency.crateName} not found")
        }
    }

    companion object {
        private val IGNORED_PROPERTIES = listOf("git", "path", "registry")
    }
}

private fun collectProperties(owner: TomlKeyValueOwner): Map<String, TomlValue> {
    return owner.entries.mapNotNull {
        val name = it.key.name ?: return@mapNotNull null
        val value = it.value ?: return@mapNotNull null
        name to value
    }.toMap()
}

private data class DependencyCrate(
    val crateName: String,
    val crateNameElement: TomlKeySegment,
    val properties: Map<String, TomlValue>
)

private sealed class DependencyTable {
    object General : DependencyTable()
    data class Specific(val crateName: String, val crateNameElement: TomlKeySegment) : DependencyTable()

    companion object {
        fun fromTomlTable(table: TomlTable): DependencyTable? {
            val key = table.header.key ?: return null
            val segments = key.segments
            val dependencyNameIndex = segments.indexOfFirst { it.isDependencyKey }

            return when {
                // [dependencies], [x86.dev-dependencies], etc.
                dependencyNameIndex == segments.lastIndex -> General
                // [dependencies.crate]
                dependencyNameIndex != -1 -> {
                    val crate = segments.getOrNull(dependencyNameIndex + 1)
                    val crateName = crate?.name
                    if (crate != null && crateName != null) {
                        Specific(crateName, crate)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }
}
