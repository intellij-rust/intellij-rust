/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl

fun VirtualFile.getContentHashIfStored(): ByteArray? =
    (PersistentFS.getInstance() as PersistentFSImpl).getContentHashIfStored(this)
