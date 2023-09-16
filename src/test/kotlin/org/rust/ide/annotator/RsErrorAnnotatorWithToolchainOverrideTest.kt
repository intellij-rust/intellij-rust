/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.annotator

class RsErrorAnnotatorWithToolchainOverrideTest : RsWithToolchainAnnotatorTestBase<Unit>(RsErrorAnnotator::class) {

    fun `test do not highlight box syntax as experimental with nightly toolchain`() = check {
        toml("rust-toolchain", """
            [toolchain]
            channel = "nightly"
        """)
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", """
                #![feature(box_syntax)]

                /*caret*/
                fn main() {
                    let world = <error descr="`box` expression syntax has been removed">box</error> "world";
                    println!("Hello, {}!", world);
                }
            """)
        }
    }
}
