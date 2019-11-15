/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.resolve

import org.toml.lang.psi.TomlLiteral

class CargoTomlPathResolveTest : CargoTomlResolveTestBase() {

    fun `test workspace members`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [workspace]
            members = [ "foo" ]
                         #^ foo
        """)
        dir("foo") {}
    }

    fun `test default workspace members`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [workspace]
            default-members = [ "foo" ]
                                  #^ foo
        """)
        dir("foo") {}
    }

    fun `test package workspace path`() = doResolveTest<TomlLiteral> {
        dir("foo") {}
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                workspace = "../foo"
                                #^ foo
            """)
        }
    }

    fun `test path 1`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [lib]
            path = "foo/lib.rs"
                          #^ foo/lib.rs
        """)
        dir("foo") {
            rust("lib.rs", "")
        }
    }

    fun `test path 2`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [[bin]]
            path = "foo/bar.rs"
                          #^ foo/bar.rs
        """)
        dir("foo") {
            rust("bar.rs", "")
        }
    }

    fun `test path 3`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [dependencies]
            bar = { path = "foo/bar" }
                                #^ foo/bar
        """)
        dir("foo") {
            dir("bar") {}
        }
    }

    fun `test path 4`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [dependencies.bar]
            path = "foo/bar"
                       #^ foo/bar
        """)
        dir("foo") {
            dir("bar") {}
        }
    }

    fun `test path 5`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [patch.crates-io]
            bar = { path = 'foo/bar' }
                                #^ foo/bar
        """)
        dir("foo") {
            dir("bar") {}
        }
    }

    fun `test build path`() = doResolveTest<TomlLiteral> {
        toml("Cargo.toml", """
            [package]
            build = '''build.rs'''
                       #^ build.rs
        """)
        rust("build.rs", "")
    }
}
