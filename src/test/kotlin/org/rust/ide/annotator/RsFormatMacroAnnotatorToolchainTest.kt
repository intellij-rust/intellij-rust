/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

import org.rust.ExpandMacros
import org.rust.ide.colors.RsColor

@ExpandMacros
class RsFormatMacroAnnotatorToolchainTest : RsWithToolchainAnnotatorTestBase<Unit>(RsFormatMacroAnnotator::class) {

    override fun setUp() {
        super.setUp()
        annotationFixture.registerSeverities(RsColor.values().map(RsColor::testSeverity))
    }

    fun `test log macro`() = check {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            log = "0.4"
        """)

        dir("src") {
            rust("main.rs", """
                use std::fmt;
                struct S;
                impl fmt::Display for S {
                    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
                }

                /*caret*/
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
    }

    fun `test log macro with target`() = check {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            log = "0.4"
        """)

        dir("src") {
            rust("main.rs", """
                use std::fmt;
                struct S;
                impl fmt::Display for S {
                    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result { unimplemented!() }
                }

                /*caret*/
                fn main() {
                    log::debug!(target: "events", "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                    log::log!(target: "events", log::Level::Warn, "<FORMAT_SPECIFIER>{}</FORMAT_SPECIFIER>", S);
                }
            """)
        }
    }
}
