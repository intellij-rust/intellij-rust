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
import org.rust.lang.core.psi.ext.findCargoPackage
import org.rust.openapiext.toPsiFile
import org.toml.lang.psi.TomlFileType

/**
 * Provides navigation from a package Cargo.toml to the workspace Cargo.toml, in addition to
 * [org.rust.ide.navigation.goto.RsGotoSuperHandler]
 */
class CargoTomlGotoSuperHandler : LanguageCodeInsightActionHandler {
    override fun isValidFor(editor: Editor?, file: PsiFile?) =
        tomlPluginIsAbiCompatible() && file?.fileType == TomlFileType

    override fun invoke(project: Project, editor: Editor, file: PsiFile) {
        gotoSuperTarget(project, file)?.navigate(true)
    }
}

private fun gotoSuperTarget(project: Project, file: PsiFile): NavigatablePsiElement? =
    if (file.name.equals("cargo.toml", ignoreCase = true)) {
        val manifestPath = file.findCargoPackage()?.workspace?.manifestPath
        file.virtualFile?.fileSystem?.findFileByPath(manifestPath.toString())?.toPsiFile(project)
    } else {
        null
    }
