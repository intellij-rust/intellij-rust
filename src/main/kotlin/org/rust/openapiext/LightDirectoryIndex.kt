/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.openapiext

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeEvent
import com.intellij.openapi.fileTypes.FileTypeListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.containers.ConcurrentBitSet

/**
 * Index with similar purpose as platform [com.intellij.util.indexing.LightDirectoryIndex].
 *
 * The main difference is atomic initialization of cache data.
 * In original [com.intellij.util.indexing.LightDirectoryIndex] consumer puts
 * data into cache non atomically, so it's possible to see not fully initialized state of cache.
 * In current implementation initializer provides already fully initialized root infos
 * so other threads can find this cache in two states:
 *   * fully initialized
 *   * not initialized at all. In this case thread calls initializer and gets new root infos
 */
class LightDirectoryIndex<T>(
    parentDisposable: Disposable,
    private val defValue: T,
    private val rootInfoInitializer: (MutableMap<VirtualFile, T>) -> Unit
) {
    @Volatile
    private var _cacheData: CacheData<T>? = null

    private val cacheData: CacheData<T>
        get() {
            val cache = _cacheData
            if (cache != null) return cache
            val rootInfo = HashMap<VirtualFile, T>()
            rootInfoInitializer(rootInfo)
            val newCache = CacheData(rootInfo, ConcurrentBitSet())
            _cacheData = newCache
            return newCache
        }

    init {
        resetIndex()
        val connection = ApplicationManager.getApplication().messageBus.connect(parentDisposable)
        connection.subscribe(FileTypeManager.TOPIC, object : FileTypeListener {
            override fun fileTypesChanged(event: FileTypeEvent) {
                resetIndex()
            }
        })

        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                for (event in events) {
                    val file = event.file
                    if (file == null || file.isDirectory) {
                        resetIndex()
                        break
                    }
                }
            }
        })
    }

    fun resetIndex() {
        _cacheData = null
    }

    fun getInfoForFile(file: VirtualFile?): T {
        if (file !is VirtualFileWithId || !file.isValid) return defValue

        val (rootInfos, nonInterestingIds) = cacheData
        @Suppress("NAME_SHADOWING")
        var file = file
        while (file != null) {
            val id = (file as VirtualFileWithId).id
            if (!nonInterestingIds.get(id)) {
                val info = rootInfos[file]
                if (info != null) {
                    return info
                }
                nonInterestingIds.set(id)
            }
            file = file.parent
        }
        return defValue
    }

    private data class CacheData<T>(
        val rootInfos: Map<VirtualFile, T>,
        val nonInterestingIds: ConcurrentBitSet
    )
}
