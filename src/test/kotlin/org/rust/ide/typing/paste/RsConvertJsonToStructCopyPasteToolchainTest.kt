/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.typing.paste

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import org.intellij.lang.annotations.Language
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTreeFromText
import org.rust.openapiext.document
import java.awt.datatransfer.StringSelection

class RsConvertJsonToStructCopyPasteToolchainTest : RsWithToolchainTestBase() {
    fun `test add derive if serde is in dependencies`() = doCopyPasteTest(
        """
        //- src/lib.rs
        use serde::{Serialize, Deserialize};
        /*caret*/
        //- Cargo.toml
        [package]
        name = "hello"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        serde = "1.0"
    """, """
        use serde::{Serialize, Deserialize};

        #[derive(Serialize, Deserialize)]
        struct Struct {
            pub a: i64,
        }
    """, """{"a": 1}"""
    )

    fun `test add derive to all structs if serde is in dependencies`() = doCopyPasteTest(
        """
        //- src/lib.rs
        use serde::{Serialize, Deserialize};
        /*caret*/
        //- Cargo.toml
        [package]
        name = "hello"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        serde = "1.0"
    """, """
        use serde::{Serialize, Deserialize};

        #[derive(Serialize, Deserialize)]
        struct Struct1 {
            pub x: i64,
        }

        #[derive(Serialize, Deserialize)]
        struct Struct2 {
            pub b: bool,
        }

        #[derive(Serialize, Deserialize)]
        struct Struct3 {
            pub a: Struct2,
            pub c: Struct1,
        }
    """, """{"a": {"b": true}, "c": {"x": 1}}"""
    )

    fun `test import serde`() = doCopyPasteTest(
        """
        //- src/lib.rs
        /*caret*/
        //- Cargo.toml
        [package]
        name = "hello"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        serde = "1.0"
    """, """
        use serde::{Deserialize, Serialize};

        #[derive(Serialize, Deserialize)]
        struct Struct1 {
            pub b: bool,
        }

        #[derive(Serialize, Deserialize)]
        struct Struct2 {
            pub a: Struct1,
        }
    """, """{"a": {"b": true}}"""
    )

    override fun setUp() {
        super.setUp()
        CONVERT_JSON_ON_PASTE.setValue(true, testRootDisposable)
    }

    private fun doCopyPasteTest(
        @Language("Rust") before: String,
        @Language("Rust") after: String,
        @Language("JSON") toPaste: String
    ) {
        val testProject = fileTreeFromText(before).create()
        CopyPasteManager.getInstance().setContents(StringSelection(toPaste))
        val file = testProject.file(testProject.fileWithCaret)

        myFixture.configureFromExistingVirtualFile(file)
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        assertEquals(after.trimIndent(), file.document!!.text)
    }
}
