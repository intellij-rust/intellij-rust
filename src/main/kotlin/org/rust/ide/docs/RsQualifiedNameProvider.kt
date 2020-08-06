/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.docs

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.parentOfType
import org.rust.lang.RsConstants.MOD_RS_FILE
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsNamedFieldDecl
import org.rust.lang.core.psi.ext.*
import org.rust.lang.core.types.ty.TyAdt
import org.rust.lang.core.types.type
import org.rust.openapiext.toPsiFile

class RsQualifiedNameProvider : QualifiedNameProvider {
    override fun getQualifiedName(element: PsiElement): String? {
        val namedElement = when (element) {
            is PsiDirectory -> element.findFile(MOD_RS_FILE) as? RsFile
            is RsQualifiedNamedElement -> element
            else -> null
        } ?: return null

        return RsQualifiedName.from(namedElement)?.toUrlPath()
    }

    override fun qualifiedNameToElement(fqn: String, project: Project): PsiElement? {
        val name = RsQualifiedName.from(fqn) ?: return null
        return name.findPsiElement(PsiManager.getInstance(project), project)
    }

    override fun adjustElementToCopy(element: PsiElement): PsiElement? = null

    override fun insertQualifiedName(fqn: String, element: PsiElement, editor: Editor, project: Project) {
        val name = getInsertionPath(element, editor, project) ?: fqn
        super.insertQualifiedName(name, element, editor, project)
    }
}

private fun getInsertionPath(element: PsiElement, editor: Editor, project: Project): String? {
    val namedElement = (element as? RsQualifiedNamedElement) ?: return null
    val name = namedElement.name ?: return null

    val file = editor.document.toPsiFile(project) as? RsFile
    val insertionMod: RsMod? = file?.findElementAt(editor.caretModel.offset)?.parentOfType()

    val basePath = when (namedElement) {
        is RsAbstractable -> namedElement.owner.qualifiedPath(insertionMod)
        is RsNamedFieldDecl -> (namedElement.parent.parent as? RsQualifiedNamedElement)?.qualifiedPath(insertionMod)
        else -> null
    }

    return basePath?.plus("::$name") ?: namedElement.qualifiedPath(insertionMod)
}

private fun RsQualifiedNamedElement.qualifiedPath(mod: RsMod?): String? = if (mod == null) {
    qualifiedName
} else {
    qualifiedNameRelativeTo(mod)
}

private fun RsAbstractableOwner.qualifiedPath(mod: RsMod?): String? = when (this) {
    is RsAbstractableOwner.Trait -> trait.qualifiedPath(mod)
    is RsAbstractableOwner.Impl -> {
        when (val typeReference = impl.typeReference) {
            is RsBaseType -> (typeReference.type as? TyAdt)?.item?.qualifiedPath(mod)
            else -> null
        }
    }
    else -> null
}
