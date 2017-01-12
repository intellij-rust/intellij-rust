package org.rust.utils

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile


fun fullyRefreshDirectory(directory: VirtualFile) {
    VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, directory)
}
