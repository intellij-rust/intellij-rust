/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PsiElementPattern
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.or
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psiElement
import org.rust.lang.core.with
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlTable
import org.toml.lang.psi.TomlTableHeader

class CargoTomlKeyReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(dependencyKeyPattern or specificDependencyKeyPattern,
            CargoDependencyReferenceProvider())
    }

    companion object {

        private const val TOML_KEY_CONTEXT_NAME = "key"

        // [dependencies]
        // regex = "1"
        //   ^
        private val dependencyKeyPattern: PsiElementPattern.Capture<TomlKey> = psiElement<TomlKey>()
            .withSuperParent(2, psiElement<TomlTable>()
                .withChild(psiElement<TomlTableHeader>()
                    .with("dependenciesCondition") { header ->
                        header.names.lastOrNull()?.isDependencyKey == true
                    }
                )
            )
        // [dependencies.regex]
        //                 ^
        private val specificDependencyKeyPattern: PsiElementPattern.Capture<TomlKey> = psiElement<TomlKey>(TOML_KEY_CONTEXT_NAME)
            .withParent(psiElement<TomlTableHeader>()
                .with("specificDependencyCondition") { header, context ->
                    val key = context?.get(TOML_KEY_CONTEXT_NAME) ?: return@with false
                    val names = header.names
                    names.getOrNull(names.size - 2)?.isDependencyKey == true && names.lastOrNull() == key
                }
            )
    }
}

private class CargoDependencyReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is TomlKey) return emptyArray()
        return arrayOf(CargoDependencyReferenceImpl(element))
    }
}

private class CargoDependencyReferenceImpl(key: TomlKey) : PsiReferenceBase<TomlKey>(key) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val file = element.containingFile?.virtualFile ?: return null
        val cargoProject = project.cargoProjects.findProjectForFile(file) ?: return null
        val crateRoot = cargoProject.workspace?.findPackage(element.text)?.libTarget?.crateRoot ?: return null
        return PsiManager.getInstance(project).findFile(crateRoot) as? RsFile
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun calculateDefaultRangeInElement(): TextRange = TextRange.from(0, element.textLength)
}
