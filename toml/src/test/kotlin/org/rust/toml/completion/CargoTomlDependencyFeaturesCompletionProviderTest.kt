/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.fileTree

// TODO these test should fail because of AST loading
class CargoTomlDependencyFeaturesCompletionProviderTest : CargoTomlCompletionTestBase() {
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test inline dependency feature`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies]
                dep-lib = { version = "0.1.0", features = ["f<caret>"] }
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies]
            dep-lib = { version = "0.1.0", features = ["foo<caret>"] }
        """)
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test inline dependency feature without literal`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies]
                dep-lib = { version = "0.1.0", features = [f<caret>] }
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies]
            dep-lib = { version = "0.1.0", features = ["foo<caret>"] }
        """)
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test table dependency feature`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies.dep-lib]
                version = "0.1.0"
                features = ["f<caret>"]
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies.dep-lib]
            version = "0.1.0"
            features = ["foo<caret>"]
        """)
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test table dependency feature without literal`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies.dep-lib]
                version = "0.1.0"
                features = [f<caret>]
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies.dep-lib]
            version = "0.1.0"
            features = ["foo<caret>"]
        """)
    }

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test for duplicates in inline dependency feature`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [dependencies]
                dep-lib = { version = "0.1.0", features = ["foo", "<caret>"] }
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    foo = []
                    bar = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [dependencies]
            dep-lib = { version = "0.1.0", features = ["foo", "bar<caret>"] }
        """)
    }
}
