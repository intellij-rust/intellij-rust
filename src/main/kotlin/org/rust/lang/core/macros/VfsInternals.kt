/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl
import com.intellij.util.BitUtil
import com.intellij.util.io.DigestUtil
import org.rust.openapiext.fileId
import org.rust.stdext.HashCode
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object VfsInternals {
    /** [com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.MUST_RELOAD_CONTENT] */
    @VisibleForTesting
    val MUST_RELOAD_CONTENT: Int = 0x08

    /** [com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor.getContentHashDigest] */
    private fun getContentHashDigest(): MessageDigest = DigestUtil.sha1()

    @VisibleForTesting
    fun isMarkedForContentReload(file: VirtualFile) =
        BitUtil.isSet(PersistentFS.getInstance().getFileAttributes(file.fileId), MUST_RELOAD_CONTENT)

    @VisibleForTesting
    @Throws(IOException::class)
    fun reloadFileIfNeeded(file: VirtualFile) {
        if (isMarkedForContentReload(file)) {
            file.contentsToByteArray(false)
        }
    }

    /** `null` means disabled hashing or invalid file */
    @VisibleForTesting
    fun getContentHashIfStored(file: VirtualFile): HashCode? =
        PersistentFSImpl.getContentHashIfStored(file)?.let { HashCode.fromByteArray(it) }

    /** [com.intellij.openapi.vfs.newvfs.persistent.PersistentFSContentAccessor.calculateHash] */
    fun calculateContentHash(fileContent: ByteArray): HashCode {
        val digest = getContentHashDigest()
        digest.update(fileContent.size.toString().toByteArray(StandardCharsets.UTF_8))
        digest.update("\u0000".toByteArray(StandardCharsets.UTF_8))
        digest.update(fileContent)
        return HashCode.fromByteArray(digest.digest())
    }

    sealed class ContentHashResult {
        object Disabled : ContentHashResult()
        class Ok(val hash: HashCode) : ContentHashResult()
        class Err(val error: IOException) : ContentHashResult()
    }
}
