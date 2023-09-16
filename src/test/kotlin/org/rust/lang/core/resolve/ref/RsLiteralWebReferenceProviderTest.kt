/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.resolve.ref

import com.intellij.openapi.paths.WebReference
import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.RsTestBase
import org.rust.StdlibLikeProjectDescriptor

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

    fun `test for invalid rust github issue attr`() = checkUrlReferences("""
        #![unstable(issue = "<caret>0")]

        #[unstable(feature = "foo", issue = "<caret>0")]
        fn foo() { }

        #[rustc_const_unstable(feature = "bar", issue = "<caret>0")]
        fn bar() { }
    """, null, null, null)

    @ProjectDescriptor(StdlibLikeProjectDescriptor::class)
    fun `test rust github issue attr`() = checkUrlReferences("""
        #![unstable(issue = "<info><caret>0</info>")]

        #[unstable(issue = "<info><caret>0</info>")]
        fn a() { }

        #[unstable(issue = "<caret>")]
        fn b() { }

        #[foo(issue = "<caret>")]
        fn c() { }

        #[rustc_const_unstable(issue = "<info><caret>0</info>")]
        fn d() { }

        #[rustc_const_unstable(issue = "<caret>none")]
        fn e() { }

        #[rustc_const_unstable(number = "<caret>none")]
        fn f() { }

        #[cfg_attr(target_env = "", unstable(issue = "<info><caret>0</info>"))]
        fn g() { }
    """, RUST_GITHUB_ISSUE_URL, RUST_GITHUB_ISSUE_URL, null, null, RUST_GITHUB_ISSUE_URL, null, null, RUST_GITHUB_ISSUE_URL)

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

    companion object {
        private const val RUST_GITHUB_ISSUE_URL = "https://github.com/rust-lang/rust/issues/0"
    }
}
