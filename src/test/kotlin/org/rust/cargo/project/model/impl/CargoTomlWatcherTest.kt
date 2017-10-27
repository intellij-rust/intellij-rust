/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.application.runWriteAction
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

        watcher.checkTriggered(newRenameEvent(tomlFile, "Foo.toml"))
        watcher.checkTriggered(newRenameEvent(tomlFile, "Cargo.toml"))
    }

    fun `test lockfile modifications`() {
        val watcher = CargoTomlWatcher { counter += 1 }

        val (lockFile, createEvent) = newCreateEvent("Cargo.lock")
        watcher.checkTriggered(createEvent)
        watcher.checkTriggered(newChangeEvent(lockFile))
    }

    fun `test implicit targets`() {
        val watcher = CargoTomlWatcher { counter += 1 }

        // src/bin/*.rs
        val (binFile, createEvent) = newCreateEvent("src/bin/foo.rs")
        watcher.checkTriggered(createEvent)
        watcher.checkNotTriggered(newChangeEvent(binFile))

        // src/bin/*/main.rs
        watcher.checkTriggered(newCreateEvent("src/bin/foo/main.rs").second)

        // src/main.rs
        watcher.checkTriggered(newCreateEvent("src/main.rs").second)
        watcher.checkNotTriggered(newCreateEvent("prefix_src/main.rs").second)

        // src/lib.rs
        watcher.checkTriggered(newCreateEvent("src/lib.rs").second)
        watcher.checkNotTriggered(newCreateEvent("prefix_src/lib.rs").second)
        watcher.checkNotTriggered(newCreateEvent("src/bar.rs").second)

        // benches/*.rs, examples/*.rs, tests/*.rs
        watcher.checkTriggered(newCreateEvent("benches/foo.rs").second)
        watcher.checkTriggered(newCreateEvent("examples/foo.rs").second)
        watcher.checkTriggered(newCreateEvent("tests/foo.rs").second)
        watcher.checkNotTriggered(newCreateEvent("prefix_tests/foo.rs").second)

        // benches/*/main.rs, examples/*/main.rs, tests/*/main.rs
        watcher.checkTriggered(newCreateEvent("benches/foo/main.rs").second)
        watcher.checkTriggered(newCreateEvent("examples/foo/main.rs").second)
        watcher.checkTriggered(newCreateEvent("tests/foo/main.rs").second)

        // build.rs
        watcher.checkTriggered(newCreateEvent("build.rs").second)
        watcher.checkNotTriggered(newCreateEvent("prefix_build.rs").second)
    }

    fun `test event properties`() {
        val watcher = CargoTomlWatcher { counter += 1 }
        val (binFile, createEvent) = newCreateEvent("src/foo.rs")
        watcher.checkNotTriggered(createEvent)
        watcher.checkTriggered(newRenameEvent(binFile, "main.rs"))
        watcher.checkNotTriggered(VFilePropertyChangeEvent(null, binFile, VirtualFile.PROP_WRITABLE, false, true, true))
        watcher.checkTriggered(newRenameEvent(binFile, "foo.rs"))
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

    private fun newRenameEvent(vFile: VirtualFile, newName: String): VFilePropertyChangeEvent {
        val oldName = vFile.name
        runWriteAction { vFile.rename(null, newName) }
        return VFilePropertyChangeEvent(null, vFile, VirtualFile.PROP_NAME, oldName, newName, true)
    }
}
