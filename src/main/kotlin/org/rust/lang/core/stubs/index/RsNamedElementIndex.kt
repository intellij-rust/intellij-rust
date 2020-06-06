/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs.index

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.stubs.RsFileStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsNamedElementIndex : StringStubIndexExtension<RsNamedElement>() {
    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsNamedElement> = KEY

    companion object {
        val KEY: StubIndexKey<String, RsNamedElement> =
            StubIndexKey.createIndexKey("org.rust.lang.core.stubs.index.RustNamedElementIndex")

        fun findElementsByName(
            project: Project,
            target: String,
            scope: GlobalSearchScope = GlobalSearchScope.allScope(project)
        ): Collection<RsNamedElement> {
            checkCommitIsNotInProgress(project)
            return getElements(KEY, target, project, scope)
        }
    }
}
