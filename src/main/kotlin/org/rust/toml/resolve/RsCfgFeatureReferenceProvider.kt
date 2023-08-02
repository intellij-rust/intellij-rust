/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.kind
import org.rust.toml.getPackageCargoTomlFile

/**
 * Consider "main.rs":
 * ```
 * #[cfg(feature = "foo")]
 *                //^ Provides a reference for "foo"
 * fn foo() {}
 * ```
 */
class RsCfgFeatureReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is RsLitExpr) return emptyArray()
        return arrayOf(RsCfgFeatureReferenceReference(element))
    }
}

private class RsCfgFeatureReferenceReference(element: RsLitExpr) : PsiPolyVariantReferenceBase<RsLitExpr>(element) {
    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> {
        val literalValue = (element.kind as? RsLiteralKind.String)?.value ?: return ResolveResult.EMPTY_ARRAY
        val toml = element.containingCargoPackage?.getPackageCargoTomlFile(element.project) ?: return ResolveResult.EMPTY_ARRAY
        return toml.resolveFeature(literalValue)
    }
}
