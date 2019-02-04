/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import org.rust.RsTestBase

abstract class CargoTomlWatcherTestBase : RsTestBase() {

    protected fun newCreateEvent(name: String): Pair<VirtualFile, VFileCreateEvent> {
        val vFile = myFixture.tempDirFixture.createFile("proj/$name")
        return vFile to VFileCreateEvent(null, vFile.parent, vFile.name, false, null, true, true)
    }
}
