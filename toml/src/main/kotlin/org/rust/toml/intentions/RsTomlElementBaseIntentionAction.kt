/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.rust.toml.tomlPluginIsAbiCompatible
import org.toml.ide.intentions.TomlElementBaseIntentionAction

abstract class RsTomlElementBaseIntentionAction<Ctx> : TomlElementBaseIntentionAction<Ctx>() {
    final override fun findApplicableContext(project: Project, editor: Editor, element: PsiElement): Ctx? {
        if (!tomlPluginIsAbiCompatible()) return null
        return findApplicableContextInternal(project, editor, element)
    }

    protected abstract fun findApplicableContextInternal(project: Project, editor: Editor, element: PsiElement): Ctx?
}
