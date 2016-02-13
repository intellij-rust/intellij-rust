package org.rust.cargo.commands

import com.intellij.openapi.projectRoots.SdkType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.PlatformTestCase
import org.rust.cargo.project.RustSdkType
import org.assertj.core.api.Assertions.assertThat

class CargoTestCase : PlatformTestCase() {
    val dataPath = "src/test/resources/org/rust/cargo/commands/fixtures/hello"
    private val cargoPath: String? get() = SdkType.findInstance(RustSdkType::class.java).suggestHomePath()?.let {
        FileUtil.join(it, "bin", "cargo")
    }

    override fun runTest() {
        if (cargoPath != null) {
            super.runTest()
        } else {
            System.err?.println("Skipping $name, no cargo found")
        }
    }

    fun testCargoMetadata() {
        val data = LocalFileSystem.getInstance().findFileByPath(dataPath)!!
        copyDirContentsTo(data, project.baseDir)
        data.refresh(false, true)

        val description = Cargo.fromProjectDirectory(cargoPath!!, project.basePath!!).fullProjectDescription()

        assertThat(description.modules.size == 2)
        assertThat(description.libraries.size == 1)
    }
}

