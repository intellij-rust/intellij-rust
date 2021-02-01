/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.completion

import org.rust.ProjectDescriptor
import org.rust.WithDependencyRustProjectDescriptor
import org.rust.fileTree

class CargoTomlFeatureDependencyCompletionProviderTest : CargoTomlCompletionTestBase() {
    fun `test feature completion (in literal)`() = doSingleCompletion("""
        [features]
        foo = []
        bar = ["f<caret>"]
    """, """
        [features]
        foo = []
        bar = ["foo<caret>"]
    """)

    fun `test feature completion (without literal) 1`() = doSingleCompletion("""
        [features]
        foo = []
        bar = [f<caret>]
    """, """
        [features]
        foo = []
        bar = ["foo<caret>"]
    """)

    fun `test feature completion (without literal) 2`() = doSingleCompletion("""
        [features]
        foo = []
        bar = []
        baz = ["bar", f<caret>]
    """, """
        [features]
        foo = []
        bar = []
        baz = ["bar", "foo<caret>"]
    """)

    fun `test feature single completion without itself`() = doSingleCompletion("""
        [features]
        foo = []
        bar = [<caret>]
    """, """
        [features]
        foo = []
        bar = ["foo<caret>"]
    """)

    // TODO the test should fail because of AST loading
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test feature in another package`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [features]
                foo = [ "dep<caret>" ]

                [dependencies]
                dep-lib = "0.1.0"
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    feature_foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [features]
            foo = [ "dep-lib/feature_foo<caret>" ]

            [dependencies]
            dep-lib = "0.1.0"
        """)
    }

    // TODO the test should fail because of AST loading
    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    fun `test feature in another package (without literal)`() {
        val fileTree = fileTree {
            toml("Cargo.toml", """
                [features]
                foo = [ dep<caret> ]

                [dependencies]
                dep-lib = "0.1.0"
            """)
            dir("dep-lib") {
                toml("Cargo.toml", """
                    [features]
                    feature_foo = []
                """)
            }
        }
        doSingleCompletionByFileTree(fileTree, """
            [features]
            foo = [ "dep-lib/feature_foo<caret>" ]

            [dependencies]
            dep-lib = "0.1.0"
        """)
    }
}
