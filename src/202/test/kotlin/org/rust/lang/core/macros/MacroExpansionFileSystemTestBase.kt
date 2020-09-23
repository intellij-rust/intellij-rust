/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.macros

import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.testFramework.fixtures.BasePlatformTestCase

abstract class MacroExpansionFileSystemTestBase : BasePlatformTestCase() {
    protected val persistentFSFlagsHolder: Class<*> get() = PersistentFS::class.java
}
