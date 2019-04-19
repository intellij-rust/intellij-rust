/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.allTargets
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.openapiext.toPsiFile
import org.rust.stdext.buildList

object CargoTestLocator : SMTestLocator {
    private const val NAME_SEPARATOR: String = "::"
    private const val TEST_PROTOCOL: String = "cargo:test"
    private const val SUITE_PROTOCOL: String = "cargo:suite"

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<PsiElement>> {
        if (protocol != TEST_PROTOCOL && protocol != SUITE_PROTOCOL) return emptyList()
        val qualifiedName = toQualifiedName(path)

        // `RsQualifiedNamedElement.qualifiedName` starts with Cargo target name, so if the `qualifiedName` doesn't
        // contain the name separator, we are looking for a target with the corresponding name
        if (!qualifiedName.contains(NAME_SEPARATOR)) {
            return project.cargoProjects.allTargets
                .filter { it.pkg.origin == PackageOrigin.WORKSPACE }
                .filter { it.normName == qualifiedName }
                .mapNotNull { it.crateRoot?.toPsiFile(project) }
                .map { PsiLocation.fromPsiElement<PsiElement>(it) }
                .toList()
        }

        return buildList {
            val name = qualifiedName.substringAfterLast(NAME_SEPARATOR)
            for (element in RsNamedElementIndex.findElementsByName(project, name, scope)) {
                val sourceElement = when (element) {
                    is RsFunction, is RsMod -> element as? RsQualifiedNamedElement
                    is RsModDeclItem -> element.reference.resolve() as? RsFile
                    else -> null
                }
                if (sourceElement?.qualifiedName == qualifiedName) {
                    add(PsiLocation.fromPsiElement(sourceElement))
                }
            }
        }
    }

    fun getTestFnUrl(name: String): String = "$TEST_PROTOCOL://$name"

    fun getTestFnUrl(function: RsFunction): String =
        getTestFnUrl(function.qualifiedName ?: "")

    fun getTestModUrl(name: String): String = "$SUITE_PROTOCOL://$name"

    fun getTestModUrl(mod: RsMod): String =
        getTestModUrl(mod.qualifiedName ?: "")

    private fun toQualifiedName(path: String): String {
        val targetName = path.substringBefore(NAME_SEPARATOR).substringBeforeLast("-")
        if (!path.contains(NAME_SEPARATOR)) return targetName
        val qualifiedName = path.substringAfter(NAME_SEPARATOR)
        return "$targetName$NAME_SEPARATOR$qualifiedName"
    }
}
