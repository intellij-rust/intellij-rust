/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.openapi.paths.WebReference
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.ext.TomlLiteralKind
import org.toml.lang.psi.ext.kind

class CargoTomlUrlReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        val literal = element as? TomlLiteral ?: return emptyArray()
        val kind = (literal.kind as? TomlLiteralKind.String) ?: return emptyArray()
        val value = kind.value ?: return emptyArray()

        if (value.startsWith("http://") || value.startsWith("https://")) {
            return arrayOf(WebReference(element, value))
        }
        return emptyArray()
    }
}
