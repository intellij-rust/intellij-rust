/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.rust.toml.findDependencyTomlFile
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

/**
 * Consider `Cargo.toml`:
 * ```
 * [features]
 * foo = []
 * bar = [ "foo" ]
 *         #^ Provides a reference for "foo"
 * baz = [ "some_dependency_package/feature_name" ]
 *         #^ and for "some_dependency_package/feature_name"
 * ```
 *
 * @see [org.rust.toml.completion.CargoTomlFeatureDependencyCompletionProvider]
 */
class CargoTomlFeatureDependencyReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is TomlLiteral) return emptyArray()
        return arrayOf(CargoTomlFeatureDependencyReference(element))
    }
}

private class CargoTomlFeatureDependencyReference(element: TomlLiteral) : PsiPolyVariantReferenceBase<TomlLiteral>(element) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val element = element
        val literalValue = (element.kind as? TomlLiteralKind.String)?.value ?: return ResolveResult.EMPTY_ARRAY
        return if ("/" in literalValue) {
            val (firstSegment, featureName) = literalValue.split("/", limit = 2)
                .takeIf { it.size == 2 }
                ?: return ResolveResult.EMPTY_ARRAY
            val depName = firstSegment.removeSuffix("?")

            val depToml = findDependencyTomlFile(element, depName) ?: return ResolveResult.EMPTY_ARRAY
            depToml.resolveFeature(featureName)
        } else {
            val depOnly = literalValue.startsWith("dep:")
            val tomlFile = element.containingFile as? TomlFile ?: return ResolveResult.EMPTY_ARRAY
            tomlFile.resolveFeature(literalValue.removePrefix("dep:"), depOnly)
        }
    }

    override fun handleElementRename(newElementName: String): PsiElement {
        val valueRange = rangeInElement
        val unescapedLiteralValue = valueRange.substring(element.node.text)
        val separatorIndex = unescapedLiteralValue.indexOf("/")
        val range = if (separatorIndex != -1) {
            TextRange(valueRange.startOffset + separatorIndex + 1, valueRange.endOffset)
        } else {
            rangeInElement
        }
        return ElementManipulators.getManipulator(element)!!
            .handleContentChange(myElement, range, newElementName)!!
    }
}
