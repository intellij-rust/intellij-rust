/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.lang.core.psi.RsAssocTypeBinding
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.deleteWithSurroundingCommaAndWhitespace

class RemoveAssocTypeBindingFix(binding: PsiElement) : RsQuickFixBase<PsiElement>(binding) {
    override fun getFamilyName(): String = text
    override fun getText(): String = RsBundle.message("intention.name.remove.redundant.associated.type")

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val binding = element as? RsAssocTypeBinding ?: return
        val parent = binding.parent as? RsTypeArgumentList

        binding.deleteWithSurroundingCommaAndWhitespace()

        if (parent != null && parent.childOfType<RsElement>() == null) {
            parent.delete()
        }
    }
}
