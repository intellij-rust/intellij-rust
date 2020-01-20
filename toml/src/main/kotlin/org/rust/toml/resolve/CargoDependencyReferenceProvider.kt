/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceProvider
import com.intellij.util.ProcessingContext
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.RsFile
import org.rust.openapiext.toPsiFile
import org.toml.lang.psi.TomlKey

class CargoDependencyReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is TomlKey) return emptyArray()
        return arrayOf(CargoDependencyReferenceImpl(element))
    }
}

private class CargoDependencyReferenceImpl(key: TomlKey) : PsiReferenceBase<TomlKey>(key) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val file = element.containingFile?.virtualFile ?: return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        val crateRoot = cargoProject.workspace?.findPackage(element.text)?.libTarget?.crateRoot ?: return null
        return crateRoot.toPsiFile(project) as? RsFile
    }

    override fun getVariants(): Array<out LookupElement> = LookupElement.EMPTY_ARRAY

    override fun calculateDefaultRangeInElement(): TextRange = TextRange.from(0, element.textLength)
}
