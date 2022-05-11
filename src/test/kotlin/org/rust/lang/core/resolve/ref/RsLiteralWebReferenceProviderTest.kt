/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.paths.WebReference
import org.intellij.lang.annotations.Language
import org.rust.RsTestBase

class RsLiteralWebReferenceProviderTest : RsTestBase() {

    fun `test single url`() = checkUrlReferences("""
        fn main() {
            "<info><caret>http://localhost:8080</info>";
        }
    """, "http://localhost:8080")

    fun `test multiple urls`() = checkUrlReferences("""
        fn main() {
            "some text <info><caret>http://localhost:8080</info> <info><caret>https:///github.com/foo/bar</info>";
        }
    """, "http://localhost:8080", "https:///github.com/foo/bar")

    fun `test no urls`() = checkUrlReferences("""
        fn main() {
            "some text <caret>http : // localhost:8080";
            "<caret>some://localhost:8080";
        }
    """, null, null)

    fun `test escaping`() = checkUrlReferences("""
        fn main() {
            "<info><caret>https:\u{002F}\u{002F}github.com/foo/bar</info>";
        }
    """, "https://github.com/foo/bar")

    fun `test raw string`() = checkUrlReferences("""
        fn main() {
            r"some text <info><caret>http://localhost:8080</info> <caret>https:\u{002F}\u{002F}github.com/foo/bar";
        }
    """, "http://localhost:8080", null)

    fun `test binary string`() = checkUrlReferences("""
        fn main() {
            b"some text <info><caret>http://localhost:8080</info> <info><caret>https:\u{002F}\u{002F}github.com/foo/bar</info>";
        }
    """, "http://localhost:8080", "https://github.com/foo/bar")

    private fun checkUrlReferences(@Language("Rust") code: String, vararg expectedUrls: String?) {
        InlineFile(code, "main.rs")
        val allCarets = myFixture.editor.caretModel.allCarets
        check(allCarets.isNotEmpty()) {
            "You should specify at least one caret position"
        }

        myFixture.testHighlighting(
            /* checkWarnings = */ false,
            /* checkInfos = */ true,
            /* checkWeakWarnings = */ false
        )

        val actualUrls = allCarets.map {
            (myFixture.file.findReferenceAt(it.offset) as? WebReference)?.url
        }

        assertEquals(expectedUrls.toList(), actualUrls)
    }
}
