/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml

import org.rust.FileTree
import org.rust.RsTestBase
import org.rust.fileTree

class CargoTomlRenameTest : RsTestBase() {

    fun `test rename path 1`() {
        val before = fileTree {
            toml("Cargo.toml", """
                [lib]
                path = "foo.rs"
            """)
            rust("foo.rs", "")
        }
        val after = fileTree {
            toml("Cargo.toml", """
                [lib]
                path = "bar.rs"
            """)
            rust("bar.rs", "")
        }
        doTest(before, after, "foo.rs", "bar")
    }

    fun `test rename path 2`() {
        val before = fileTree {
            toml("Cargo.toml", """
                [dependencies]
                bar = { path = '''foo/bar''' }
            """)
            dir("foo") {
                dir("bar") {}
            }
        }

        val after = fileTree {
            toml(
                "Cargo.toml", """
                [dependencies]
                bar = { path = '''foo/baz''' }
            """
            )
            dir("foo") {
                dir("baz") {}
            }
        }
        doTest(before, after, "foo/bar", "baz")
    }

    private fun doTest(before: FileTree, after: FileTree, elementPath: String, newName: String) {
        val testProject = before.create()
        val element = testProject.psiFile(elementPath)
        myFixture.renameElement(element, newName)
        after.assertEquals(testProject.root)
    }
}
