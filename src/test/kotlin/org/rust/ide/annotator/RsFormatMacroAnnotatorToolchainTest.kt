/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import com.intellij.codeInsight.daemon.impl.SeveritiesProvider
import com.intellij.ide.annotator.AnnotatorBase
import com.intellij.ide.annotator.TestSeverityProvider
import org.rust.FileTree
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.ide.colors.RsColor

class RsFormatMacroAnnotatorToolchainTest : RsWithToolchainTestBase() {
    override fun setUp() {
        super.setUp()
        AnnotatorBase.enableAnnotator(RsFormatMacroAnnotator::class.java, testRootDisposable)
        val testSeverityProvider = TestSeverityProvider(RsColor.values().map(RsColor::testSeverity))
        SeveritiesProvider.EP_NAME.point.registerExtension(testSeverityProvider, testRootDisposable)
    }

    fun `test log macro`() = checkErrors(fileTree {
        toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                log = "0.4"
            """)

        dir("src") {
            file("main.rs", """
                use std::fmt;
                struct S;
                impl fmt::Display for S {
                    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
                }

                fn main() {
                    log::trace!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::debug!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::info!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::warn!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::error!("<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::log!(log::Level::Warn, "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                }
            """)
        }
    })

    fun `test log macro with target`() = checkErrors(fileTree {
        toml("Cargo.toml", """
                [package]
                name = "hello"
                version = "0.1.0"
                authors = []

                [dependencies]
                log = "0.4"
            """)

        dir("src") {
            file("main.rs", """
                use std::fmt;
                struct S;
                impl fmt::Display for S {
                    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
                }

                fn main() {
                    log::debug!(target: "events", "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::log!(target: "events", log::Level::Warn, "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                }
            """)
        }
    })

    private fun checkErrors(fileTree: FileTree) {
        fileTree.create()
        val filePath = "src/main.rs"
        myFixture.openFileInEditor(cargoProjectDirectory.findFileByRelativePath(filePath)!!)
        myFixture.checkHighlighting()
    }
}
