package org.rust.cargo.runconfig.test

import com.intellij.execution.Location
import com.intellij.execution.PsiLocation
import com.intellij.execution.testframework.sm.runner.SMTestLocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.qualifiedName
import org.rust.lang.core.resolve.ResolveEngine

/**
 * Url format:
 *  - test functions: `cargo:test://{test function fq name}`
 *
 * **It is preferable to use `getUrl` methods instead of manually constructing urls.**
 */
object CargoTestLocator : SMTestLocator {
    const val TEST_PROTOCOL = "cargo:test"

    fun getLocation(url: String, project: Project, scope: GlobalSearchScope): List<Location<PsiElement>> =
        getLocation(
            requireNotNull(VirtualFileManager.extractProtocol(url)) { "couldn't extract protocol" },
            VirtualFileManager.extractPath(url),
            project,
            scope)

    override fun getLocation(
        protocol: String,
        path: String,
        project: Project,
        scope: GlobalSearchScope
    ): List<Location<PsiElement>> {
        if (protocol == TEST_PROTOCOL) {
            val resolveResult = ResolveEngine.resolve(path, project) ?: return emptyList()
            return listOf(PsiLocation.fromPsiElement<PsiElement>(project, resolveResult.element))
        }

        return emptyList()
    }

    fun getUrl(function: RsFunction): String = "$TEST_PROTOCOL://${function.qualifiedName}"
}
