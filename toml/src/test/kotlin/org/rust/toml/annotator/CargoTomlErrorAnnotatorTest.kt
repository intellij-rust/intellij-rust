/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.annotator

import org.intellij.lang.annotations.Language
import org.rust.toml.crates.Crate
import org.rust.toml.crates.withMockedCrateResolver

class RsErrorAnnotatorTest : TomlAnnotatorTestBase(CargoTomlErrorAnnotator::class) {
    fun `test missing dependency`() = check("""
        [dependencies]
        <error descr="Crate doesn't exist">foo</error> = "1.0.0"
    """)

    private fun check(
        @Language("TOML") code: String,
        vararg crates: Crate
    ) {
        withMockedCrateResolver(createCrateResolver(crates.toList())) {
            checkErrors(code.trimIndent())
        }
    }
}
