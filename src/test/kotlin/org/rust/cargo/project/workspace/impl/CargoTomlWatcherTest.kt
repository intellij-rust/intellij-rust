/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.workspace.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import org.rust.lang.RsTestBase

class CargoTomlWatcherTest : RsTestBase() {
    private var counter = 0

    fun `test toml modifications`() {
        val watcher = CargoTomlWatcher { counter += 1 }

        val (tomlFile, createEvent) = newCreateEvent("Cargo.toml")
        watcher.checkTriggered(createEvent)

        watcher.checkNotTriggered(newCreateEvent("Rustfmt.toml").second)

        watcher.checkTriggered(newChangeEvent(tomlFile))

        watcher.checkTriggered(newRenameEvent(tomlFile))
    }

    fun `test lockfile modifications`() {
        val watcher = CargoTomlWatcher { counter += 1 }

        val (lockFile, createEvent) = newCreateEvent("Cargo.lock")
        watcher.checkTriggered(createEvent)
        watcher.checkTriggered(newChangeEvent(lockFile))
    }

    fun `test implicit targets`() {
        val watcher = CargoTomlWatcher { counter += 1 }

        val (binFile, createEvent) = newCreateEvent("src/bin/foo.rs")
        watcher.checkTriggered(createEvent)
        watcher.checkNotTriggered(newChangeEvent(binFile))

        watcher.checkNotTriggered(newCreateEvent("src/bar.rs").second)

        watcher.checkTriggered(newCreateEvent("tests/foo.rs").second)

        watcher.checkTriggered(newCreateEvent("build.rs").second)
    }

    private fun CargoTomlWatcher.checkTriggered(event: VFileEvent) {
        val old = counter
        after(listOf(event))
        check(counter == old + 1) {
            "Watcher ignored $event"
        }
    }

    private fun CargoTomlWatcher.checkNotTriggered(event: VFileEvent) {
        val old = counter
        after(listOf(event))
        check(counter == old) {
            "Watcher should have ignored $event"
        }
    }

    private fun newCreateEvent(name: String): Pair<VirtualFile, VFileCreateEvent> {
        val vFile = myFixture.tempDirFixture.createFile("proj/$name")
        return vFile to VFileCreateEvent(null, vFile.parent, vFile.name, false, true)
    }

    private fun newChangeEvent(vFile: VirtualFile) = VFileContentChangeEvent(null, vFile, vFile.modificationStamp - 1, vFile.modificationStamp, true)

    private fun newRenameEvent(vFile: VirtualFile) = VFilePropertyChangeEvent(null, vFile, VirtualFile.PROP_NAME, "Foo.toml", "Cargo.toml", true)
}
