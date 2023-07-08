/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.RsBundle
import org.rust.ide.utils.expandStructFields
import org.rust.ide.utils.expandTupleStructFields
import org.rust.lang.core.psi.RsPatStruct
import org.rust.lang.core.psi.RsPatTupleStruct
import org.rust.lang.core.psi.RsPsiFactory

class AddStructFieldsPatFix(
    element: PsiElement
) : RsQuickFixBase<PsiElement>(element) {
    override fun getText() = RsBundle.message("intention.name.add.missing.fields")

    override fun getFamilyName() = text

    override fun invoke(project: Project, editor: Editor?, element: PsiElement) {
        val factory = RsPsiFactory(project)
        if (element is RsPatStruct) {
            expandStructFields(factory, element)
        } else if (element is RsPatTupleStruct) {
            expandTupleStructFields(factory, editor, element)
        }
    }
}
