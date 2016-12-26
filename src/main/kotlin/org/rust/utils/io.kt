package org.rust.utils

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.io.DataInput
import java.io.DataOutput

fun <T> DataOutput.writeNullable(value: T?, inner: DataOutput.(T) -> Unit) =
    if (value == null) {
        writeBoolean(true)
    } else {
        writeBoolean(false)
        inner(value)
    }

fun <T> DataInput.readNullable(inner: DataInput.() -> T): T? = if (readBoolean()) null else inner()

fun <T> DataOutput.writeList(value: List<T>, inner: DataOutput.(T) -> Unit) {
    writeInt(value.size)
    for (v in value) {
        inner(v)
    }
}

fun <T> DataInput.readList(inner: DataInput.() -> T): List<T> {
    val size = readInt()
    return (0 until size).map { inner() }.toList()
}

fun fullyRefreshDirectory(directory: VirtualFile) {
    VfsUtil.markDirtyAndRefresh(/* async = */ false, /* recursive = */ true, /* reloadChildren = */ true, directory)
}
