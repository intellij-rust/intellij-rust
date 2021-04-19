/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import org.rust.stdext.intersects
import org.rust.toml.isDependencyKey
import org.rust.toml.stringValue
import org.toml.lang.psi.*
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind
import org.toml.lang.psi.ext.name

abstract class CargoDependencyCrateVisitor: TomlVisitor() {
    abstract fun visitDependency(dependency: DependencyCrate)

    override fun visitKeyValue(element: TomlKeyValue) {
        val table = element.parent as? TomlTable ?: return
        val depTable = DependencyTable.fromTomlTable(table) ?: return
        if (depTable !is DependencyTable.General) return

        val segment = element.key.segments.firstOrNull() ?: return
        val name = segment.name ?: return
        val value = element.value ?: return
        if (value is TomlLiteral && value.kind is TomlLiteralKind.String) {
            visitDependency(DependencyCrate(name, segment, mapOf("version" to value)))
        } else if (value is TomlInlineTable) {
            val pkg = value.getPackageKeyValue()
            val (originalNameElement, originalName) = when {
                pkg != null -> pkg.value to pkg.value?.stringValue
                else -> segment to name
            }
            if (originalName != null && originalNameElement != null) {
                visitDependency(DependencyCrate(originalName, originalNameElement, collectProperties(value)))
            }
        }
    }

    override fun visitTable(element: TomlTable) {
        val depTable = DependencyTable.fromTomlTable(element) ?: return
        if (depTable !is DependencyTable.Specific) return

        visitDependency(DependencyCrate(depTable.crateName, depTable.crateNameElement, collectProperties(element)))
    }
}

private fun collectProperties(owner: TomlKeyValueOwner): Map<String, TomlValue> {
    return owner.entries.mapNotNull {
        val name = it.key.name ?: return@mapNotNull null
        val value = it.value ?: return@mapNotNull null
        name to value
    }.toMap()
}

data class DependencyCrate(
    val crateName: String,
    val crateNameElement: TomlElement,
    val properties: Map<String, TomlValue>
) {
    /**
     * Is this crate from another source than crates.io?
     */
    fun isForeign(): Boolean = properties.keys.intersects(FOREIGN_PROPERTIES)

    companion object {
        private val FOREIGN_PROPERTIES = listOf("git", "path", "registry")
    }
}

private sealed class DependencyTable {
    object General : DependencyTable()
    data class Specific(val crateName: String, val crateNameElement: TomlElement) : DependencyTable()

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
                    val pkg = table.getPackageKeyValue()
                    val (nameElement, name) = when {
                        pkg != null -> pkg.value to pkg.value?.stringValue
                        else -> {
                            val segment = segments.getOrNull(dependencyNameIndex + 1)
                            segment to segment?.name
                        }
                    }

                    if (nameElement != null && name != null) {
                        Specific(name, nameElement)
                    } else {
                        null
                    }
                }
                else -> null
            }
        }
    }
}

/**
 * Return the `package` key from a table, if present.
 * Example:
 *
 * ```
 * [dependencies.foo]
 * <selection>package = "bar"</selection>
 *
 * [dependencies]
 * foo = { <selection>package = "bar"</selection> }
 * ```
 */
private fun TomlKeyValueOwner.getPackageKeyValue(): TomlKeyValue? = entries.firstOrNull {
    it.key.segments.firstOrNull()?.name == "package"
}
