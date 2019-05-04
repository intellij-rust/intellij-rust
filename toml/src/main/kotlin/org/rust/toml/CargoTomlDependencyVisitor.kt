/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import org.toml.lang.psi.*

abstract class CargoTomlDependencyVisitor : PsiElementVisitor() {
    override fun visitElement(element: PsiElement?) {
        val table = element as? TomlTable ?: return
        if ((element.parent as? TomlFile)?.virtualFile?.name?.equals("cargo.toml", /*ignoreCase*/ true) == true) {
            val headerNames = table.header.names
            // Thanks to this dependencyNameIndex tactic we can also successfully match some less standard declarations
            // such as [target.x86_64-pc-windows-gnu.dependencies], or [target."x86_64/linux.json".dependencies.serde]
            val dependencyNameIndex = headerNames.indexOfFirst { it.isDependencyKey }
            if (dependencyNameIndex != -1) {
                val crate = headerNames.getOrNull(dependencyNameIndex + 1)
                if (crate != null) {
                    // Matches [dependencies.'crate'], [dev-dependencies.'crate'], etc
                    val version = table.entries.find { it.key.text == "version" }
                    visitDependency(crate, version?.value)
                } else {
                    // Matches [dependencies], [dev-dependencies], etc
                    for (dependency in table.entries) {
                        val version = when (val value = dependency.value) {
                            // crate = { version = "x.y.z" }
                            is TomlInlineTable -> value.entries.find { it.key.text == "version" }?.value
                            // crate = "x.y.z"
                            is TomlValue -> value
                            else -> null
                        }
                        visitDependency(dependency.key, version)
                    }
                }
            }
        }
    }

    open fun visitDependency(name: TomlKey, version: TomlValue?) {}
}
