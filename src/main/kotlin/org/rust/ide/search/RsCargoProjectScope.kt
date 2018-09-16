/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.search

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.DelegatingGlobalSearchScope
import com.intellij.psi.search.GlobalSearchScope
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.PackageOrigin

class RsCargoProjectScope(
    private val service: CargoProjectsService,
    baseScope: GlobalSearchScope
) : DelegatingGlobalSearchScope(baseScope) {

    override fun contains(file: VirtualFile): Boolean {
        if (!myBaseScope.contains(file)) return false
        val pkg = service.findPackageForFile(file) ?: return false
        return pkg.origin != PackageOrigin.TRANSITIVE_DEPENDENCY
    }
}
