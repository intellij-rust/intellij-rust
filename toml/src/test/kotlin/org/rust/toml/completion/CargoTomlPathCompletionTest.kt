/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.rust.fileTree

class CargoTomlPathCompletionTest : CargoTomlCompletionTestBase() {

    fun `test workspace completion 1`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [workspace]
                members = [ "fo<caret>" ]
            """)
            dir("foo") {}
            rust("foo.rs", "")
        }
        doSingleCompletionByFileTree(fileTree, """
            [workspace]
            members = [ "foo<caret>" ]
        """)
    }

    fun `test workspace completion 2`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [workspace]
                default-members = [ "fo<caret>" ]
            """)
            dir("foo") {}
            rust("foo.rs", "")
        }
        doSingleCompletionByFileTree(fileTree, """
            [workspace]
            default-members = [ "foo<caret>" ]
        """)
    }

    fun `test path 1`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [lib]
                path = "foo/li<caret>"
            """)
            dir("foo") {
                rust("lib.rs", "")
                file("lib.in", "")
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [lib]
            path = "foo/lib.rs<caret>"
        """)
    }

    fun `test path 2`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [[bin]]
                path = "foo/ba<caret>"
            """)
            dir("foo") {
                dir("bar") {
                    rust("lib.rs", "")
                }
                file("bar.txt", "")
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [[bin]]
            path = "foo/bar<caret>"
        """)
    }

    fun `test path 3`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies]
                bar = { path = "foo/ba<caret>" }
            """)
            dir("foo") {
                dir("bar") {}
                rust("bar.rs", "")
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies]
            bar = { path = "foo/bar<caret>" }
        """)
    }

    fun `test path 4`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [patch.crates-io.bar]
                path = 'foo/ba<caret>'
            """
            )
            dir("foo") {
                dir("bar") {}
                rust("bar.rs", "")
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [patch.crates-io.bar]
            path = 'foo/bar<caret>'
        """)
    }

    fun `test build path`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [package]
                build = '''bui<caret>'''
            """)
            rust("build.rs", "")
            dir("build") {}
        }
        doSingleCompletionByFileTree(fileTree, """
            [package]
            build = '''build.rs<caret>'''
        """)
    }
}
