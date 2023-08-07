/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.Language
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider
import com.intellij.psi.FileViewProviderFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.SingleRootFileViewProvider

/**
 * Hacky adjust the file limit for Rust file.
 * Coupled with [org.rust.lang.core.resolve.indexes.RsAliasIndex.getFileTypesWithSizeLimitNotApplicable].
 *
 * @see SingleRootFileViewProvider.isTooLargeForIntelligence
 */
class RsFileViewProviderFactory : FileViewProviderFactory {
    override fun createFileViewProvider(
        file: VirtualFile,
        language: Language,
        manager: PsiManager,
        eventSystemEnabled: Boolean
    ): FileViewProvider {
        val shouldAdjustFileLimit = SingleRootFileViewProvider.isTooLargeForIntelligence(file)
            && file.length <= RUST_FILE_SIZE_LIMIT_FOR_INTELLISENSE

        if (shouldAdjustFileLimit) {
            SingleRootFileViewProvider.doNotCheckFileSizeLimit(file)
        }

        return SingleRootFileViewProvider(manager, file, eventSystemEnabled)
    }
}

// Experimentally verified that 8Mb works with the default IDEA -Xmx768M. Larger values may
// lead to OOM, please verify before adjusting
private const val RUST_FILE_SIZE_LIMIT_FOR_INTELLISENSE: Int = 8 * 1024 * 1024
