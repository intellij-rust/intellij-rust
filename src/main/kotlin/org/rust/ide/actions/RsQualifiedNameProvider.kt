/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.actions

import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName

class RsQualifiedNameProvider : QualifiedNameProvider {
    override fun getQualifiedName(element: PsiElement?): String? = (element as? RsQualifiedNamedElement)?.qualifiedName

    override fun qualifiedNameToElement(fqn: String?, project: Project?): PsiElement? = null

    override fun adjustElementToCopy(element: PsiElement?): PsiElement? = null
}
