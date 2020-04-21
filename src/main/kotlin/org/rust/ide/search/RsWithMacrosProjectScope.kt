/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.rust.lang.core.macros.macroExpansionManagerIfCreated

open class RsWithMacrosScope(project: Project, scope: GlobalSearchScope) : DelegatingGlobalSearchScope(scope) {
    private val service = project.macroExpansionManagerIfCreated

    override fun contains(file: VirtualFile): Boolean {
        return super.contains(file) || service?.isExpansionFileOfCurrentProject(file) == true
    }
}

class RsWithMacrosProjectScope(project: Project) : RsWithMacrosScope(project, GlobalSearchScope.allScope(project))
