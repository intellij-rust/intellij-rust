/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

@file:Suppress("DEPRECATION")

package org.rust.lang.core.macros

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Key
import com.intellij.psi.stubs.PrebuiltStubsProvider
import com.intellij.psi.stubs.SerializedStubTree
import com.intellij.psi.stubs.SerializerNotFoundException
import com.intellij.psi.stubs.Stub
import com.intellij.util.indexing.FileContent

/**
 * Used in a couple with [MacroExpansionSharedCache] to provide macro expansion cache shared between projects.
 * This is not a real _prebuilt_ stubs provider. This extension point is used to intercept platform's
 * stub creation.
 */
@Suppress("UnstableApiUsage")
class MacroExpansionStubsProvider : PrebuiltStubsProvider {
    override fun findStub(fileContent: FileContent): SerializedStubTree? {
        return findSerializedStubForMacroExpansionFile(fileContent)
    }

    companion object {
        private val LOG = logger<MacroExpansionStubsProvider>()
        private val SERIALIZED_STUB_KEY: Key<SerializedStubTree> =
            Key.create("org.rust.lang.core.macros.MacroExpansionStubsProvider.serializedStubKey")

        private fun findSerializedStubForMacroExpansionFile(fileContent: FileContent): SerializedStubTree? {
            val file = fileContent.file
            if (!MacroExpansionManager.isExpansionFile(file)) return null

            fileContent.getUserData(SERIALIZED_STUB_KEY)?.let { return it }

            if (!MacroExpansionSharedCache.getInstance().isEnabled) return null

            val hash = file.extractMixHashAndMacroStorageVersion()
                ?.takeIf { it.second == MACRO_STORAGE_VERSION }
                ?.first
                ?: return null

            val serializedStub = MacroExpansionSharedCache.getInstance().cachedBuildStub(fileContent, hash)
            fileContent.putUserData(SERIALIZED_STUB_KEY, serializedStub)
            return serializedStub
        }

        fun findStubForMacroExpansionFile(fileContent: FileContent): Stub? {
            val serializedStub = findSerializedStubForMacroExpansionFile(fileContent) ?: return null
            return try {
                serializedStub.stub
            } catch (e: SerializerNotFoundException) {
                LOG.warn(e)
                null
            }
        }
    }
}
