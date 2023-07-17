/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.inspections

import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.rust.RsBundle
import org.toml.lang.psi.TomlPsiFactory
import org.toml.lang.psi.TomlValue

class UpdateCrateVersionFix(
    versionElement: TomlValue,
    private val version: String
): LocalQuickFixOnPsiElement(versionElement) {
    override fun getFamilyName(): String = RsBundle.message("intention.family.name.update.dependency.version")
    override fun getText(): String = RsBundle.message("intention.name.update.version.to", version)

    override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
        val factory = TomlPsiFactory(project)
        val newValue = factory.createLiteral("\"$version\"")
        startElement.replace(newValue)
    }
}
