/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.rust.lang.core.RsPsiPattern
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.kind

class RsLitExprReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(RsPsiPattern.includeMacroLiteral, RsFileReferenceProvider())
    }
}

private class RsFileReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        val stringLiteral = (element as? RsLitExpr)?.kind as? RsLiteralKind.String ?: return emptyArray()
        if (stringLiteral.isByte) return emptyArray()
        val startOffset = stringLiteral.offsets.value?.startOffset ?: return emptyArray()
        val fs = element.containingFile.originalFile.virtualFile.fileSystem
        return object : FileReferenceSet(stringLiteral.value ?: "", element, startOffset, null, fs.isCaseSensitive) {
            override fun getDefaultContexts(): MutableCollection<PsiFileSystemItem> = parentDirectoryContext
        }.allReferences
    }
}
