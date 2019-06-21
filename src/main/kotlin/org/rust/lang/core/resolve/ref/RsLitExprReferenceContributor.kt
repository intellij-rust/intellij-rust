/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.util.Condition
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.rust.lang.RsFileType
import org.rust.lang.core.RsPsiPattern.includeMacroLiteral
import org.rust.lang.core.RsPsiPattern.pathAttrLiteral
import org.rust.lang.core.or
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.ancestorStrict

class RsLitExprReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(includeMacroLiteral or pathAttrLiteral, RsFileReferenceProvider())
    }
}

private class RsFileReferenceProvider : PsiReferenceProvider() {
    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<FileReference> {
        val stringLiteral = (element as? RsLitExpr)?.kind as? RsLiteralKind.String ?: return emptyArray()
        if (stringLiteral.isByte) return emptyArray()
        val startOffset = stringLiteral.offsets.value?.startOffset ?: return emptyArray()
        val fs = element.containingFile.originalFile.virtualFile.fileSystem
        return RsLiteralFileReferenceSet(stringLiteral.value ?: "", element, startOffset, fs.isCaseSensitive).allReferences
    }
}

private class RsLiteralFileReferenceSet(
    str: String,
    element: RsLitExpr,
    startOffset: Int,
    isCaseSensitive: Boolean
) : FileReferenceSet(str, element, startOffset, null, isCaseSensitive) {

    override fun getDefaultContexts(): Collection<PsiFileSystemItem> {
        return when (val parent = element.parent) {
            is RsIncludeMacroArgument -> parentDirectoryContext
            is RsMetaItem -> {
                val item = parent.ancestorStrict<RsModDeclItem>() ?: parent.ancestorStrict<RsMod>()
                listOfNotNull(item?.containingMod?.getOwnedDirectory())
            }
            else -> emptyList()
        }
    }

    override fun getReferenceCompletionFilter(): Condition<PsiFileSystemItem> {
        return when (element.parent) {
            is RsMetaItem -> Condition { item ->
                if (item.isDirectory) return@Condition true
                item.virtualFile.fileType == RsFileType
            }
            else -> super.getReferenceCompletionFilter()
        }
    }
}
