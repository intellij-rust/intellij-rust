/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.toml.StringLiteralInsertionHandler
import org.rust.toml.findCargoPackageForCargoToml
import org.rust.toml.getPackageTomlFile
import org.rust.toml.resolve.allFeatures
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.impl.TomlKeyValueImpl

/**
 *  * Consider `Cargo.toml`:
 * ```
 * [features]
 * foo = []
 * bar = [ "f<caret>" ]
 *         #^ Provides completion for "foo"
 * ```
 *
 * @see [org.rust.toml.resolve.CargoTomlFeatureDependencyReferenceProvider]
 */
class CargoTomlFeatureDependencyCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val element = parameters.position
        val tomlFile = element.containingFile as? TomlFile ?: return

        val parentFeature = element.parentOfType<TomlKeyValueImpl>()?.key?.segments?.singleOrNull() ?: return
        for (feature in tomlFile.allFeatures()) {
            if (feature == parentFeature) continue
            result.addElement(lookupElementForFeature(feature))
        }

        val pkg = tomlFile.findCargoPackageForCargoToml() ?: return
        for (dep in pkg.dependencies) {
            if (dep.pkg.origin == PackageOrigin.STDLIB) continue
            // TODO avoid AST loading?
            for (feature in dep.pkg.getPackageTomlFile(tomlFile.project)?.allFeatures().orEmpty()) {
                result.addElement(
                    LookupElementBuilder
                        .createWithSmartPointer("${dep.pkg.name}/${feature.text}", feature)
                        .withInsertHandler(StringLiteralInsertionHandler())
                )
            }
        }
    }
}
