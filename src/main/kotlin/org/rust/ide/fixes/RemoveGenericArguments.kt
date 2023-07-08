/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.fixes

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import org.rust.ide.inspections.getTypeArgumentsAndDeclaration
import org.rust.lang.core.psi.RsTypeArgumentList
import org.rust.lang.core.psi.ext.RsMethodOrPath
import org.rust.lang.core.psi.ext.deleteWithSurroundingComma
import org.rust.lang.core.psi.ext.getNextNonCommentSibling
import org.rust.lang.core.psi.ext.startOffset

class RemoveGenericArguments(
    element: RsMethodOrPath,
    private val startIndex: Int,
    private val endIndex: Int
) : RsQuickFixBase<RsMethodOrPath>(element) {
    override fun getText(): String = RsBundle.message("intention.name.remove.redundant.generic.arguments")
    override fun getFamilyName(): String = text

    override fun invoke(project: Project, editor: Editor?, element: RsMethodOrPath) {
        val (typeArguments) = getTypeArgumentsAndDeclaration(element) ?: return
        typeArguments?.removeTypeParameters()
    }

    private fun RsTypeArgumentList.removeTypeParameters() {
        (typeReferenceList + exprList)
            .sortedBy { it.startOffset }
            .subList(startIndex, endIndex)
            .forEach { it.deleteWithSurroundingComma() }
        // If the type argument list is empty, delete it
        if (lt.getNextNonCommentSibling() == gt) {
            delete()
        }
    }
}
