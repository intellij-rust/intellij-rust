/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.stubs.index.RsNamedElementIndex

object CargoTestLocator : SMTestLocator {
    const val TEST_PROTOCOL = "cargo:test"

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<PsiElement>> {
        if (protocol == TEST_PROTOCOL) {
            val elements = RsNamedElementIndex.findElementsByName(project, path.split("::").last())
            return elements.filter { it is RsFunction }.map { PsiLocation.fromPsiElement<PsiElement>(it) }
        }

        return emptyList()
    }
    fun getTestFnUrl(name: String): String = "$TEST_PROTOCOL://$name"
    fun getTestFnUrl(function: RsFunction): String {
        return getTestFnUrl(function.crateRelativePath.configPath() ?: "")
    }
}

// We need to chop off heading colon `::`, since `crateRelativePath`
// always returns fully-qualified path
private fun String?.configPath(): String? = this?.removePrefix("::")
