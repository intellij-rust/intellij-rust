/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.model.impl.allTargets
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.psi.RsFile
import org.rust.lang.core.psi.RsModDeclItem
import org.rust.lang.core.psi.ext.RsQualifiedNamedElement
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.openapiext.toPsiFile
import org.rust.stdext.buildList

object CargoTestLocator : SMTestLocator {
    private const val NAME_SEPARATOR: String = "::"
    private const val TEST_PROTOCOL: String = "cargo:test"

    private val LINE_NUM_REGEX = Regex("""(.*?)(#\d+)?$""")

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<out PsiElement>> {
        if (protocol != TEST_PROTOCOL) return emptyList()
        val (qualifiedName, lineNum) = toQualifiedName(path)

        // `RsQualifiedNamedElement.qualifiedName` starts with Cargo target name, so if the `qualifiedName` doesn't
        // contain the name separator, we are looking for a target with the corresponding name
        if (!qualifiedName.contains(NAME_SEPARATOR)) {
            return project.cargoProjects.allTargets
                .filter { it.pkg.origin == PackageOrigin.WORKSPACE }
                .filter { it.normName == qualifiedName }
                .mapNotNull { it.crateRoot?.toPsiFile(project) }
                .map { getLocation(project, it, lineNum) }
                .toList()
        }

        return buildList {
            val name = qualifiedName.substringAfterLast(NAME_SEPARATOR)
            for (element in RsNamedElementIndex.findElementsByName(project, name, scope)) {
                val sourceElement = when (element) {
                    is RsModDeclItem -> element.reference.resolve() as? RsFile
                    is RsQualifiedNamedElement -> element
                    else -> null
                }
                if (sourceElement?.qualifiedName == qualifiedName) {
                    add(getLocation(project, sourceElement, lineNum))
                }
            }
        }
    }

    private fun getLocation(project: Project, element: PsiElement, lineNum: Int?) =
        if (lineNum != null) {
            object : PsiLocation<PsiElement>(project, element) {
                override fun getOpenFileDescriptor(): OpenFileDescriptor? =
                    virtualFile?.let { OpenFileDescriptor(project, it, lineNum, 0) }
            }
        } else {
            PsiLocation.fromPsiElement(element)
        }

    fun getTestUrl(name: String): String {
        val (qualifiedName, lineNum) = toQualifiedName(name)
        return "$TEST_PROTOCOL://$qualifiedName${lineNum?.let { "#$it" } ?: ""}"
    }

    fun getTestUrl(function: RsQualifiedNamedElement): String =
        getTestUrl(function.qualifiedName ?: "")

    fun getTestUrl(ctx: DocTestContext): String {
        val owner = ctx.owner.qualifiedName ?: ""
        return getTestUrl("$owner#${ctx.lineNumber}")
    }

    private fun toQualifiedName(path: String): Pair<String, Int?> {
        val targetName = path.substringBefore(NAME_SEPARATOR).substringBeforeLast("-")
        if (NAME_SEPARATOR !in path) return targetName to null
        val match = LINE_NUM_REGEX.matchEntire(path) ?: return targetName to null
        val qualifiedName = match.groups[1]?.value?.substringAfter(NAME_SEPARATOR)
        val lineNum = match.groups[2]?.value?.substring(1)?.toInt()

        return "$targetName$NAME_SEPARATOR$qualifiedName" to lineNum
    }
}
