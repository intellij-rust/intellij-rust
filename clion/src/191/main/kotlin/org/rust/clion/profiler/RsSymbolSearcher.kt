/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.profiler

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.profiler.model.NativeCall
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.qualifiedName
import org.rust.lang.core.stubs.index.RsNamedElementIndex
import org.rust.openapiext.getElements

class RsSymbolSearcher(private val project: Project) {
    fun find(nativeCall: NativeCall): Array<RsFunction> {
        val elements = mutableListOf<RsFunction>()
        val searchScope = GlobalSearchScope.allScope(project)
        val qualifiedPath = extractPath(nativeCall.className)

        runReadAction {
            val allDeclarations = getElements(RsNamedElementIndex.KEY, nativeCall.methodOrFunction, project, searchScope)
                .filterIsInstance<RsFunction>()
            val appropriateDeclarations = allDeclarations
                .filter { it.qualifiedName?.substringBeforeLast("::") == qualifiedPath }

            if (appropriateDeclarations.isNotEmpty()) {
                elements.addAll(appropriateDeclarations)
            } else {
                // TODO: return all or nothing?
                elements.addAll(allDeclarations)
            }
        }

        return elements.toTypedArray()
    }


    /**
     * `_<foo::bar123::foo_bar::Qux as baz>::qux` -> `foo::bar123::foo_bar`
     *
     * It doesn't work for crates/modules with inappropriate names (e.g. upper-case)
     *
     * TODO: add tests
     */
    private fun extractPath(signature: String): String = signature
        .dropWhile { !it.isLetter() }
        .takeWhile { (it.isLetterOrDigit() && it.isLowerCase()) || it == '_' || it == ':' }
        .removeSuffix("::")
}
