/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.psi.stubs.PrebuiltStubsProvider
import com.intellij.psi.stubs.SerializedStubTree
import com.intellij.util.indexing.FileContent

/**
 * Used in a couple with [MacroExpansionSharedCache] to provide macro expansion cache shared between projects.
 * This is not a real _prebuilt_ stubs provider. This extension point is used to intercept platform's
 * stub creation.
 */
class MacroExpansionStubsProvider : PrebuiltStubsProvider {
    @Suppress("UnstableApiUsage")
    override fun findStub(fileContent: FileContent): SerializedStubTree? {
        val file = fileContent.file
        if (!MacroExpansionManager.isExpansionFile(file)) return null
        if (!MacroExpansionSharedCache.getInstance().isEnabled) return null
        val hash = file.extractMixHashAndMacroStorageVersion()
            ?.takeIf { it.second == MACRO_STORAGE_VERSION }
            ?.first
            ?: return null
        return MacroExpansionSharedCache.getInstance().cachedBuildStub(fileContent, hash)
    }
}
