/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiFile
import org.rust.ide.navigation.goto.toCargoTomlPsiFile
import org.rust.lang.core.psi.ext.findCargoPackage

class CargoTomlGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor?, file: PsiFile?) = tomlPluginIsAbiCompatible() && file.isTomlFile()

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        gotoSuperTarget(project, file)?.navigate(true)
    }
}

private fun PsiFile?.isTomlFile(): Boolean = this?.fileType?.name == "TOML"

fun gotoSuperTarget(project: Project, file: PsiFile): NavigatablePsiElement? =
    if (file.name.equals("cargo.toml", ignoreCase = true)) {
        file.findCargoPackage()?.workspace?.manifestPath?.toCargoTomlPsiFile(project)
    } else {
        null
    }
