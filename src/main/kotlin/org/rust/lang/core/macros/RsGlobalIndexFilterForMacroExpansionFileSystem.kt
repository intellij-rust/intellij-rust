/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.search.FilenameIndex
import com.intellij.util.indexing.GlobalIndexFilter
import com.intellij.util.indexing.IndexId

/**
 * Disables some indexes for Rust macro expansions (i.e. for files in [MacroExpansionFileSystem])
 */
@Suppress("UnstableApiUsage")
class RsGlobalIndexFilterForMacroExpansionFileSystem : GlobalIndexFilter {

    /** Please, bump [MACRO_STORAGE_VERSION] if you change this set */
    private val disabledIndices: Set<IndexId<*, *>> = setOf(
        // `IdIndex` is disabled only for performance reasons.
        // Note that `IdIndex` is used in reference search implementation, so stuff like
        // `find usages` will not work in macro expansion. If you need reference search
        // in macro expansions, you should enable `IdIndex`
        IdIndex.NAME,

        // `TrigramIndex` is disabled for performance reasons and in order to exclude
        // macro expansions from full-text search results (`Find in Path`, `Ctr+Shift+F`)
        TrigramIndex.INDEX_ID,

        // `FilenameIndex` is disabled in order to exclude macro expansions from filename
        // search results (`Ctr+Shift+N`)
        @Suppress("DEPRECATION", "removal")
        FilenameIndex.NAME
    )

    override fun isExcludedFromIndex(virtualFile: VirtualFile, indexId: IndexId<*, *>): Boolean =
        indexId in disabledIndices && virtualFile.fileSystem is MacroExpansionFileSystem

    /**
     * In theory this method should be implement like `return indexId in disabledIndices`.
     * But with such implementation it will force full re-indexation after installing/uninstalling
     * the plugin. Wa can avoid it.
     *
     * We disable indices only for files in [MacroExpansionFileSystem] that are under our control.
     * It's enough to bump [MACRO_STORAGE_VERSION] - old files will be deleted from [MacroExpansionFileSystem],
     * hence indices will be dropped.
     */
    override fun affectsIndex(indexId: IndexId<*, *>): Boolean = false

    override fun getVersion(): Int = 0
}
