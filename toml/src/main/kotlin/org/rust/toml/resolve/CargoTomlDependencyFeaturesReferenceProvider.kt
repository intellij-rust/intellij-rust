/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.rust.toml.containingDependencyKey
import org.rust.toml.findDependencyTomlFile
import org.toml.lang.psi.TomlArray
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/**
 * Consider `Cargo.toml`:
 * ```
 * [dependencies]
 * foo = { version = "*", features = ["bar"] }
 *                                    #^ Provides a reference for "bar"
 *
 * [dependencies.foo]
 * features = ["baz"]
 *             #^ Provides a reference for "baz"
 * ```
 *
 * @see [org.rust.toml.completion.CargoTomlDependencyFeaturesCompletionProvider]
 */
class CargoTomlDependencyFeaturesReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is TomlLiteral) return emptyArray()
        return arrayOf(CargoTomlDependencyFeatureReference(element))
    }
}

private class CargoTomlDependencyFeatureReference(element: TomlLiteral) : PsiPolyVariantReferenceBase<TomlLiteral>(element) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val literalValue = (element.kind as? TomlLiteralKind.String)?.value ?: return ResolveResult.EMPTY_ARRAY
        val parentArray = element.parent as? TomlArray ?: return ResolveResult.EMPTY_ARRAY
        val pkgName = parentArray.containingDependencyKey?.text ?: return ResolveResult.EMPTY_ARRAY

        val depToml = findDependencyTomlFile(element, pkgName) ?: return ResolveResult.EMPTY_ARRAY
        return depToml.resolveFeature(literalValue)
    }
}
