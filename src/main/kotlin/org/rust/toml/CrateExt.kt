/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.rust.lang.core.crate.Crate
import org.rust.openapiext.checkWriteAccessAllowed
import org.toml.lang.psi.*

/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

/**
 * Adds dependency [name] with version [version] to the corresponding `[dependencies]` section
 * in the corresponding `Cargo.toml` file if the dependency doesn't exist already. If it does,
 * update the features of the dependency with [features] if required.
 *
 * For example, if [name] = "tokio", [version] = "1.0.0" and [features] = "full", it inserts
 * ```
 * [dependencies]
 * tokio = { version = "1.0.0", features = ["full"] }
 * ```
 */
fun Crate.addCargoDependency(name: String, version: String, features: List<String> = emptyList()) {
    checkWriteAccessAllowed()

    val cargoToml = cargoTarget?.pkg?.getPackageCargoTomlFile(project) ?: return
    val factory = TomlPsiFactory(project)

    val featuresArray = features.joinToString(prefix = "[", separator = ", ", postfix = "]") { "\"$it\"" }

    when (val existingDependency = cargoToml.findDependencyElement(name)) {
        is TomlKeyValueOwner -> {
            updateDependencyFeatures(factory, existingDependency, features)
        }
        is TomlLiteral -> {
            val newVersion = existingDependency.stringValue ?: version
            val newEntry = factory.createInlineTable("""version = "$newVersion", features = $featuresArray""")
            existingDependency.replace(newEntry)
        }
        else -> {
            val existingDependencies = cargoToml.tableList.find {
                it.header.key?.stringValue == "dependencies"
            }
            val dependencies = existingDependencies ?: run {
                val newDependenciesTable = factory.createTable("dependencies")
                cargoToml.add(factory.createWhitespace("\n"))
                cargoToml.add(newDependenciesTable) as TomlTable
            }
            val newDependencyKeyValue = if (features.isEmpty()) {
                factory.createKeyValue(name, version)
            } else {
                factory.createKeyValue(name, """{ version = "$version", features = $featuresArray }""")
            }

            dependencies.add(factory.createWhitespace("\n"))
            dependencies.add(newDependencyKeyValue)
        }
    }
}

private fun updateDependencyFeatures(factory: TomlPsiFactory, table: TomlKeyValueOwner, features: List<String>) {
    val featuresEntry = table.entries.find { entry -> entry.key.stringValue == "features" }
    if (featuresEntry == null) {
        val featuresArray = features.joinToString(prefix = "[", separator = ", ", postfix = "]") { "\"$it\"" }
        val newEntry = factory.createKeyValue("features", featuresArray)
        val newTable = (table.entries + listOf(newEntry)).joinToString(separator=", ") {
            """${it.key.text} = ${it.value?.text}"""
        }
        table.replace(factory.createInlineTable(newTable))
    } else {
        val existingFeatures = (featuresEntry.value as? TomlArray)?.elements
            ?.mapNotNull { value -> value.stringValue }
            ?: emptyList()
        val newFeatures = (existingFeatures + features).distinct()
        val newFeaturesArray = newFeatures.joinToString(prefix = "[", separator = ", ", postfix = "]") { "\"$it\"" }
        featuresEntry.replace(factory.createKeyValue("features", newFeaturesArray))
    }
}
