/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.lang.core.completion.RsCompletionTestFixture
import org.rust.toml.jsonSchema.CargoTomlJsonSchemaFileProvider

abstract class CargoTomlCompletionTestBase : RsTestBase() {

    protected lateinit var completionFixture: RsCompletionTestFixture

    override fun setUp() {
        super.setUp()
        completionFixture = CargoTomlCompletionTestFixture(myFixture)
        completionFixture.setUp()
    }

    override fun tearDown() {
        completionFixture.tearDown()
        super.tearDown()
    }

    protected fun doSingleCompletion(
        @Language("TOML") before: String,
        @Language("TOML") after: String
    ) = completionFixture.doSingleCompletion(before, after)

    protected fun doSingleCompletionByFileTree(
        fileTree: FileTree,
        @Language("TOML") after: String
    ) = completionFixture.doSingleCompletionByFileTree(fileTree, after)

    protected fun checkNoCompletion(@Language("TOML") code: String) = completionFixture.checkNoCompletion(code)

    private class CargoTomlCompletionTestFixture(
        fixture: CodeInsightTestFixture
    ) : RsCompletionTestFixture(fixture, "Cargo.toml") {

        override fun setUp() {
            super.setUp()
            val url = CargoTomlJsonSchemaFileProvider::class.java.getResource(CargoTomlJsonSchemaFileProvider.SCHEMA_PATH)
            if (url != null) {
                val path = VfsUtil.convertFromUrl(url).substringAfter("://")
                VfsRootAccess.allowRootAccess(testRootDisposable, path)
            }
        }

        override fun checkAstNotLoaded(fileFilter: (VirtualFile) -> Boolean) {
            val newFilter = { file: VirtualFile ->
                // TODO: generalize this approach to take into account all JSON schema files
                fileFilter(file) && !file.path.endsWith(CargoTomlJsonSchemaFileProvider.SCHEMA_PATH)
            }
            super.checkAstNotLoaded(newFilter)
        }
    }
}
