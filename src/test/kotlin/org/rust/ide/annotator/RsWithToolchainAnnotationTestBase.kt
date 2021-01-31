/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapiext.Testmark
import org.rust.FileTree
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree

abstract class RsWithToolchainAnnotationTestBase<C> : RsWithToolchainTestBase() {

    protected lateinit var annotationFixture: RsAnnotationTestFixture<C>

    override fun setUp() {
        super.setUp()
        annotationFixture = createAnnotationFixture()
        annotationFixture.setUp()
    }

    override fun tearDown() {
        annotationFixture.tearDown()
        super.tearDown()
    }

    protected abstract fun createAnnotationFixture(): RsAnnotationTestFixture<C>

    protected open fun check(
        context: C? = null,
        checkWarn: Boolean = true,
        checkInfo: Boolean = false,
        checkWeakWarn: Boolean = false,
        ignoreExtraHighlighting: Boolean = false,
        testmark: Testmark? = null,
        builder: FileTreeBuilder.() -> Unit
    ) {
        val file = configureProject(fileTree(builder), context)
        annotationFixture.checkByFile(file, context, checkWarn, checkInfo, checkWeakWarn, ignoreExtraHighlighting, testmark)
    }

    /**
     * Configures project from given [fileTree] and returns virtual file for checking
     */
    protected open fun configureProject(fileTree: FileTree, context: C?): VirtualFile {
        val testProject = fileTree.create()
        return testProject.file(testProject.fileWithCaret)
    }
}
