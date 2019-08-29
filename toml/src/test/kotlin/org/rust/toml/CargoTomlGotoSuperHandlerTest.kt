/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import org.rust.RsTestBase
import org.rust.fileTreeFromText

class CargoTomlGotoSuperHandlerTest : RsTestBase() {

    // Test for `RsGotoSuperHandler`
    fun `test go from a crate root to cargo toml`() = checkNavigationInFiles("""
    //- main.rs
        /*caret*/fn main() {}

    //- Cargo.toml
        [package]
        name = "example"
    """, "Cargo.toml")

    // Test for CargoTomlGotoSuperHandler
    fun `test go from package cargo toml to workspace cargo toml`() = checkNavigationInFiles("""
    //- Cargo.toml
        /*caret*/[package]
        name = "example"
    //- workspace/Cargo.toml
        [workspace]
    """, "workspace/Cargo.toml")

    private fun checkNavigationInFiles(fileTreeText: String, expectedFilePath: String) {
        fileTreeFromText(fileTreeText).createAndOpenFileWithCaretMarker()
        myFixture.performEditorAction("GotoSuperMethod")
        val file = FileDocumentManager.getInstance().getFile(
            FileEditorManager.getInstance(project).selectedTextEditor!!.document)!!
        check(file.path.endsWith(expectedFilePath)) { "Expected `$expectedFilePath`, actual `${file.path}`" }
    }
}
