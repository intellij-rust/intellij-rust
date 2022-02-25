/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.lineMarkers

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.PathUtil
import org.intellij.lang.annotations.Language

class CargoExecutableRunLineMarkerContributorTest : RsLineMarkerProviderTestBase() {

    fun `test main binary`() = doTest("main.rs", """
        fn main() {} // - Run 'Run test-package'
    """)

    fun `test additional binary`() = doTest("bin/a.rs", """
        fn main() {} // - Run 'Run test-package'
    """)

    fun `test binary example`() = doTest("example/a.rs", """
        fn main() {} // - Run 'Run test-package'
    """)

    fun `test build script`() = doTest("build.rs", """
        fn main() {}
    """)

    fun `test library`() = doTest("lib.rs", """
        fn main() {}
    """)

    fun `test inner main`() = doTest("main.rs", """
        fn foo() {
            fn main() {}
        }
    """)

    fun `test no_main attribute`() = doTest("main.rs", """
        #![no_main]

        fn main() {}
    """)

    private fun doTest(filePath: String, @Language("Rust") text: String) {
        val dirs = PathUtil.getParentPath(filePath)
        val fileName = PathUtil.getFileName(filePath)

        val rootDir = myFixture.findFileInTempDir(".")
        var dir = rootDir
        if (dirs.isNotEmpty()) {
            dir = runWriteAction { VfsUtil.createDirectoryIfMissing(rootDir, dirs) }
        }
        val file = runWriteAction {
            val file = dir.createChildData(dir.fileSystem, fileName)
            VfsUtil.saveText(file, text)
            file
        }
        lineMarkerTestHelper.doTestFromFile(file)
    }
}
