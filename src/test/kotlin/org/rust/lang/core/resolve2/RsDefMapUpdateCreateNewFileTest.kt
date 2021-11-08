/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve2

import com.intellij.openapi.application.runWriteAction
import com.intellij.psi.PsiDirectory
import org.intellij.lang.annotations.Language
import org.rust.ExpandMacros
import org.rust.fileTreeFromText
import org.rust.openapiext.toPsiDirectory

/** Tests whether [CrateDefMap] should be updated after creation of new file */
@ExpandMacros
class RsDefMapUpdateCreateNewFileTest : RsDefMapUpdateTestBase() {

    private fun createFile(parent: PsiDirectory, fileToCreate: String): () -> Unit = {
        runWriteAction {
            parent.createFile(fileToCreate)
        }
    }

    private fun doTest(
        fileToCreate: String,
        @Language("Rust") before: String,
        shouldChange: Boolean = true
    ) {
        val testProject = fileTreeFromText(before).create()
        val root = testProject.root.toPsiDirectory(myFixture.project)!!
        doTest(createFile(root, fileToCreate), shouldChange)
    }

    fun `test create file for mod decl`() = doTest("foo.rs", """
    //- main.rs
        mod foo;
    """)

    fun `test create file for include! macro`() = doTest("foo.rs", """
    //- main.rs
        include!("foo.rs");
    """)

    fun `test create non relevant file`() = doTest("foo.rs", """
    //- main.rs
        fn main() {}
    """, shouldChange = false)
}
