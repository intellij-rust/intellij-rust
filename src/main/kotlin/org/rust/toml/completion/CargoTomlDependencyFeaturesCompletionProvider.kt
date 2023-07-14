/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.ext.ancestorOrSelf
import org.rust.stdext.mapNotNullToSet
import org.rust.toml.containingDependencyKey
import org.rust.toml.findDependencyTomlFile
import org.rust.toml.resolve.allFeatures
import org.rust.toml.stringValue
import org.toml.lang.psi.TomlArray

/**
 * Consider `Cargo.toml`:
 * ```
 * [dependencies]
 * foo = { version = "*", features = ["<caret>"] }
 *                                    #^ Provides completion here
 *
 * [dependencies.foo]
 * features = ["<caret>"]
 *             #^ Provides completion here
 * ```
 *
 * @see [org.rust.toml.resolve.CargoTomlDependencyFeaturesReferenceProvider]
 */
class CargoTomlDependencyFeaturesCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val containingArray = parameters.position.ancestorOrSelf<TomlArray>() ?: return
        val pkgName = containingArray.containingDependencyKey?.text ?: return

        val depToml = findDependencyTomlFile(containingArray, pkgName) ?: return
        val originalArray = CompletionUtil.getOriginalElement(containingArray) ?: return
        // TODO avoid AST loading?
        val presentFeatures = originalArray.elements.mapNotNullToSet { it.stringValue }
        result.addAllElements(
            depToml.allFeatures()
                .filter { it.name !in presentFeatures }
                .map { lookupElementForFeature(it) }
                .toList()
        )
    }
}
