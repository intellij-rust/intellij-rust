/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.fileTree
import org.rust.lang.core.resolve.RsResolveTestBase
import org.toml.lang.psi.TomlKey

@ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
class CargoTomlKeyResolveTest : RsResolveTestBase() {
    fun `test in dependencies block`() = checkResolve("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies]
        dep-lib = "0.1.0"
        #^ /dep-lib/lib.rs
    """)

    fun `test in dev dependencies block`() = checkResolve("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dev-dependencies]
        dep-lib = "0.1.0"
        #^ /dep-lib/lib.rs
    """)

    fun `test specific dependency`() = checkResolve("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [dependencies.dep-lib]
                       #^ /dep-lib/lib.rs
        version = "0.1.0"

    """)

    private fun checkResolve(@Language("TOML") code: String) {
        val fileTree = fileTree { toml("Cargo.toml", code) }
        stubOnlyResolve<TomlKey>(fileTree)
    }
}
