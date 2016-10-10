package org.rust.utils

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile

/**
 * Transforms seconds into milliseconds
 */
val Int.seconds: Int
    get() = this * 1000

/**
 * Converts [Boolean] to [Int]
 */
val Boolean.int: Int
    get() = if (this === true) 1 else 0

fun VirtualFile.findFileByMaybeRelativePath(path: String): VirtualFile? =
    if (FileUtil.isAbsolute(path))
        fileSystem.findFileByPath(path)
    else
        findFileByRelativePath(path)
