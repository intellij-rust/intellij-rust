/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.indexes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import org.rust.lang.core.macros.macroExpansionManager
import org.rust.lang.core.psi.RsMacro
import org.rust.lang.core.psi.ext.RsMod
import org.rust.lang.core.psi.ext.hasMacroExport
import org.rust.lang.core.psi.ext.isRustcDocOnlyMacro
import org.rust.lang.core.psi.isValidProjectMember
import org.rust.lang.core.psi.rustStructureModificationTracker
import org.rust.lang.core.stubs.RsFileStub
import org.rust.lang.core.stubs.RsMacroStub
import org.rust.openapiext.checkCommitIsNotInProgress
import org.rust.openapiext.getElements

class RsMacroIndex : StringStubIndexExtension<RsMacro>() {

    override fun getVersion(): Int = RsFileStub.Type.stubVersion
    override fun getKey(): StubIndexKey<String, RsMacro> = KEY

    companion object {

        private val KEY: StubIndexKey<String, RsMacro> =
            StubIndexKey.createIndexKey("org.rust.lang.core.resolve.indexes.RsMacroIndex")

        private const val SINGLE_KEY = "#"

        fun index(stub: RsMacroStub, sink: IndexSink) {
            if (stub.name != null && (stub.psi.hasMacroExport || stub.psi.isRustcDocOnlyMacro)) {
                sink.occurrence(KEY, SINGLE_KEY)
            }
        }

        fun allExportedMacros(project: Project): Map<RsMod, List<RsMacro>> {
            checkCommitIsNotInProgress(project)
            val cacheKey = if (project.macroExpansionManager.expansionState != null) EXPORTED_MACROS_KEY else EXPORTED_KEY
            return CachedValuesManager.getManager(project).getCachedValue(project, cacheKey, {
                val result = HashMap<RsMod, MutableList<RsMacro>>()
                val keys = StubIndex.getInstance().getAllKeys(KEY, project)
                for (key in keys) {
                    val elements = getElements(KEY, key, project, GlobalSearchScope.allScope(project))
                    for (element in elements) {
                        if (element.isValidProjectMember) {
                            val crateRoot = element.crateRoot ?: continue
                            result.getOrPut(crateRoot, ::ArrayList) += element
                        }
                    }
                }

                // remove macros with same names (may exist under #[cfg] attrs)
                for (macros in result.values) {
                    val names = hashSetOf<String>()
                    val duplicatedNames = hashSetOf<String>()
                    for (macro in macros) {
                        val name = macro.name
                        if (name != null && !names.add(name)) {
                            duplicatedNames.add(name)
                        }
                    }
                    macros.removeIf { it.name in duplicatedNames }
                }
                CachedValueProvider.Result.create(result, project.rustStructureModificationTracker)
            }, false)
        }

        private val EXPORTED_KEY: Key<CachedValue<Map<RsMod, List<RsMacro>>>> = Key.create("EXPORTED_KEY")
        private val EXPORTED_MACROS_KEY: Key<CachedValue<Map<RsMod, List<RsMacro>>>> = Key.create("EXPORTED_MACROS_KEY")
    }
}
