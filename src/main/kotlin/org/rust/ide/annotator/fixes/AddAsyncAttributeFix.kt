/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.utils.import.RsImportHelper
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.containingCrate
import org.rust.lang.core.resolve.knownItems

class AddAsyncRecursionAttributeFix(function: RsFunction): LocalQuickFixAndIntentionActionOnPsiElement(function) {

    override fun getText(): String = "Add `async_recursion` attribute"
    override fun getFamilyName(): String = text

    override fun invoke(
        project: Project,
        file: PsiFile,
        editor: Editor?,
        startElement: PsiElement,
        endElement: PsiElement
    ) {
        val function = startElement as? RsFunction ?: return
        val procMacro = function.knownItems
            .findItem<RsFunction>("async_recursion::async_recursion", isStd = false) ?: return
        RsImportHelper.importElement(function, procMacro)
        val attr = RsPsiFactory(project).createOuterAttr("async_recursion")
        function.addAfter(attr, null)
    }

    companion object {
        fun createIfCompatible(function: RsFunction): AddAsyncRecursionAttributeFix? {
            if (!hasAsyncRecursionDependency(function)) return null
            return AddAsyncRecursionAttributeFix(function)
        }

        private fun hasAsyncRecursionDependency(context: RsElement): Boolean {
            val crate = context.containingCrate
            return crate.dependencies.any { it.normName == "async_recursion" }
        }
    }
}
