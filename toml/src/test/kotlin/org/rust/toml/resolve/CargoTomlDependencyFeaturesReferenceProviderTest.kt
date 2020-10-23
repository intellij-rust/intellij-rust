/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.toml.lang.psi.TomlLiteral

class CargoTomlDependencyFeaturesReferenceProviderTest : CargoTomlResolveTestBase() {
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test inline dependency feature`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [dependencies]
            dep-lib = { version = "0.1.0", features = ["feature_foo"] }
                                                       #^ /dep-lib/Cargo.toml
        """)
        dir("dep-lib") {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [features]
                feature_foo = []
            """)
        }
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test table dependency feature`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [dependencies.dep-lib]
            version = "0.1.0"
            features = ["feature_foo"]
                        #^ /dep-lib/Cargo.toml
        """)
        dir("dep-lib") {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [features]
                feature_foo = []
            """)
        }
    }
}
