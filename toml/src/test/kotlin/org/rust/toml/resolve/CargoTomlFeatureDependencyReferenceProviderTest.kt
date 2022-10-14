/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import org.intellij.lang.annotations.Language
import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.toml.lang.psi.TomlKeySegment
import org.toml.lang.psi.TomlLiteral

class CargoTomlFeatureDependencyReferenceProviderTest : CargoTomlResolveTestBase() {
    fun `test feature in the same package 1`() = checkResolve("""
        [package]
        name = "intellij-rust-test"
        version = "0.1.0"
        authors = []

        [features]
        foo = []
        #X
        bar = [ "foo" ]
                #^
    """)

    fun `test feature in the same package 2`() = checkResolve("""
        [features]
        foo = []
        bar = []
        #X
        baz = [ "foo", "bar" ]
                       #^
    """)

    fun `test optional dependency as a feature in the same package 1`() = checkResolve("""
        [dependencies]
        bar = { version = "*", optional = true }
        #X
        [features]
        foo = ["bar" ]
               #^
    """)

    fun `test optional dependency as a feature in the same package 2`() = checkResolve("""
        [dependencies.bar]
                     #X
        version = "1"
        optional = true

        [features]
        foo = ["bar" ]
               #^
    """)

    fun `test optional dependency as a namespaced feature in the same package 1`() = checkResolve("""
        [dependencies]
        bar = { version = "*", optional = true }
        #X
        [features]
        foo = ["dep:bar" ]
               #^
    """)

    fun `test optional dependency as a namespaced feature in the same package 2`() = checkResolve("""
        [dependencies.bar]
                     #X
        version = "1"
        optional = true

        [features]
        foo = ["dep:bar" ]
               #^
    """)

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test feature in another package`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [features]
            foo = [ "dep-lib/feature_foo" ]
                    #^ /dep-lib/Cargo.toml

            [dependencies]
            dep-lib = "0.1.0"
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
    fun `test optional dependency as a feature in another package 1`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [features]
            foo = [ "dep-lib/trans-lib" ]
                    #^ /dep-lib/Cargo.toml

            [dependencies]
            dep-lib = "0.1.0"
        """)
        dir("dep-lib") {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies]
                trans-lib = { version = "0.1.0", optional = true }
            """)
        }
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test optional dependency as a feature in another package 2`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [features]
            foo = [ "dep-lib/trans-lib" ]
                    #^ /dep-lib/Cargo.toml

            [dependencies]
            dep-lib = "0.1.0"
        """)
        dir("dep-lib") {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [dependencies.trans-lib]
                version = "0.1.0"
                optional = true
            """)
        }
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test weak dependency feature in another package`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            name = "intellij-rust-test"
            version = "0.1.0"
            authors = []

            [features]
            foo = [ "dep-lib?/feature_foo" ]
                    #^ /dep-lib/Cargo.toml

            [dependencies]
            dep-lib = { version = "0.1.0", optional = true }
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

    private fun checkResolve(@Language("TOML") code: String) = checkByCodeToml<TomlLiteral, TomlKeySegment>(code)
}
