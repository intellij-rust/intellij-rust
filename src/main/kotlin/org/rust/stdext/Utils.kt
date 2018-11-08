/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */


package org.rust.stdext

import com.intellij.openapi.vfs.VirtualFile

/**
 * Just a way to nudge Kotlin's type checker in the right direction
 */
@Suppress("NOTHING_TO_INLINE")
inline fun <T> typeAscription(t: T): T = t

inline fun <T> VirtualFile.applyWithSymlink(f: (VirtualFile) -> T?): T? {
    return f(this) ?: f(canonicalFile ?: return null)
}
