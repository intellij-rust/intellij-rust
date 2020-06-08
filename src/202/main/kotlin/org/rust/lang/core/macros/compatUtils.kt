/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFSImpl

// BACKCOMPAT: 2020.1. Inline it
fun VirtualFile.getContentHashIfStored(): ByteArray? = PersistentFSImpl.getContentHashIfStored(this)
