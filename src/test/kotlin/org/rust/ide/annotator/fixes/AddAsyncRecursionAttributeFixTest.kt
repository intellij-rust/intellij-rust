/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator.fixes

import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.ide.annotator.AnnotatorBase
import org.rust.ide.annotator.RsErrorAnnotator
import org.rust.openapiext.document

class AddAsyncRecursionAttributeFixTest : RsWithToolchainTestBase() {

    fun `test simple`() = checkFix("Add `async_recursion` attribute",
        fileTree {
            toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []
                edition = "2018"

                [dependencies]
                async-recursion = "1"
            """)
            dir("src") {
                rust("lib.rs", """
                    async fn func() {
                        func().await/*caret*/;
                    }
                """)
            }
        }, """
        use async_recursion::async_recursion;

        #[async_recursion]
        async fn func() {
            func().await;
        }
    """)

    override fun setUp() {
        super.setUp()
        AnnotatorBase.enableAnnotator(RsErrorAnnotator::class.java, testRootDisposable)
    }

    @Suppress("SameParameterValue")
    private fun checkFix(
        fixName: String,
        before: FileTree,
        @Language("Rust") after: String,
    ) {
        val testProject = before.create()
        val file = testProject.file(testProject.fileWithCaret)
        myFixture.configureFromExistingVirtualFile(file)

        myFixture.launchAction(myFixture.findSingleIntention(fixName))

        assertEquals(after.trimIndent(), file.document!!.text)
    }
}
