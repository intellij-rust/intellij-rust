/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.testFramework.LightPlatformTestCase
import org.rust.RsTestBase
import org.rust.fileTree

class CargoProjectOpenProcessorTest : RsTestBase() {

    fun `test open project via Cargo toml file`() {
        val testDir = fileTree {
            dir("crate") {
                toml("Cargo.toml", "")
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }.create(project, LightPlatformTestCase.getSourceRoot())

        val crateDir = testDir.file("crate/Cargo.toml")
        checkFileCanBeOpenedAsProject(crateDir)
    }

    fun `test open project via directory with Cargo toml file`() {
        val testDir = fileTree {
            dir("crate") {
                toml("Cargo.toml", "")
                dir("src") {
                    rust("lib.rs", "")
                }
            }
        }.create(project, LightPlatformTestCase.getSourceRoot())

        val crateDir = testDir.file("crate")
        checkFileCanBeOpenedAsProject(crateDir)
    }

    private fun checkFileCanBeOpenedAsProject(file: VirtualFile) {
        val processor = ProjectOpenProcessor.EXTENSION_POINT_NAME.extensionList.find { it is CargoProjectOpenProcessor }
            ?: error("CargoProjectOpenProcessor is not registered in plugin.xml")
        check(processor.canOpenProject(file)) {
            "Cargo project cannot be opened via `$file`"
        }
    }
}
