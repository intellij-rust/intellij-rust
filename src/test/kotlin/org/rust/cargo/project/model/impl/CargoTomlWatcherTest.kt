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
import org.rust.RsTestBase

class CargoTomlWatcherTest : RsTestBase() {
    fun `test toml modifications`() {
        val (tomlFile, createEvent) = newCreateEvent("Cargo.toml")
        checkTriggered(createEvent)

        checkNotTriggered(newCreateEvent("Rustfmt.toml").second)

        checkTriggered(newChangeEvent(tomlFile))

        checkTriggered(newRenameEvent(tomlFile, "Foo.toml"))
        checkTriggered(newRenameEvent(tomlFile, "Cargo.toml"))
    }

    fun `test lockfile modifications`() {
        val (lockFile, createEvent) = newCreateEvent("Cargo.lock")
        checkTriggered(createEvent)
        checkTriggered(newChangeEvent(lockFile))
    }

    fun `test implicit targets`() {
        // src/bin/*.rs
        val (binFile, createEvent) = newCreateEvent("src/bin/foo.rs")
        checkTriggered(createEvent)
        checkNotTriggered(newChangeEvent(binFile))

        // src/bin/*/main.rs
        checkTriggered(newCreateEvent("src/bin/foo/main.rs").second)

        // src/main.rs
        checkTriggered(newCreateEvent("src/main.rs").second)
        checkNotTriggered(newCreateEvent("prefix_src/main.rs").second)

        // src/lib.rs
        checkTriggered(newCreateEvent("src/lib.rs").second)
        checkNotTriggered(newCreateEvent("prefix_src/lib.rs").second)
        checkNotTriggered(newCreateEvent("src/bar.rs").second)

        // benches/*.rs, examples/*.rs, tests/*.rs
        checkTriggered(newCreateEvent("benches/foo.rs").second)
        checkTriggered(newCreateEvent("examples/foo.rs").second)
        checkTriggered(newCreateEvent("tests/foo.rs").second)
        checkNotTriggered(newCreateEvent("prefix_tests/foo.rs").second)

        // benches/*/main.rs, examples/*/main.rs, tests/*/main.rs
        checkTriggered(newCreateEvent("benches/foo/main.rs").second)
        checkTriggered(newCreateEvent("examples/foo/main.rs").second)
        checkTriggered(newCreateEvent("tests/foo/main.rs").second)

        // build.rs
        checkTriggered(newCreateEvent("build.rs").second)
        checkNotTriggered(newCreateEvent("prefix_build.rs").second)
    }

    fun `test event properties`() {
        val (binFile, createEvent) = newCreateEvent("src/foo.rs")
        checkNotTriggered(createEvent)
        checkTriggered(newRenameEvent(binFile, "main.rs"))
        checkNotTriggered(VFilePropertyChangeEvent(null, binFile, VirtualFile.PROP_WRITABLE, false, true, true))
        checkTriggered(newRenameEvent(binFile, "foo.rs"))
    }

    private fun checkTriggered(event: VFileEvent) {
        check(CargoTomlWatcher.isInterestingEvent(event)) {
            "Watcher ignored $event"
        }
    }

    private fun checkNotTriggered(event: VFileEvent) {
        check(!CargoTomlWatcher.isInterestingEvent(event)) {
            "Watcher should have ignored $event"
        }
    }

    private fun newCreateEvent(name: String): Pair<VirtualFile, VFileCreateEvent> {
        val vFile = myFixture.tempDirFixture.createFile("proj/$name")
        return vFile to VFileCreateEvent(null, vFile.parent, vFile.name, false, null, null, true, null)
    }

    private fun newChangeEvent(vFile: VirtualFile) = VFileContentChangeEvent(null, vFile, vFile.modificationStamp - 1, vFile.modificationStamp, true)

    private fun newRenameEvent(vFile: VirtualFile, newName: String): VFilePropertyChangeEvent {
        val oldName = vFile.name
        runWriteAction { vFile.rename(null, newName) }
        return VFilePropertyChangeEvent(null, vFile, VirtualFile.PROP_NAME, oldName, newName, true)
    }
}
