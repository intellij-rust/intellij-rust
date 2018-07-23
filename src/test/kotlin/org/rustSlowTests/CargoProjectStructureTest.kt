/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustSlowTests

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.util.ui.tree.TreeUtil
import org.rust.FileTreeBuilder
import org.rust.MinRustcVersion
import org.rust.cargo.CargoFeatures
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.settings.RustProjectSettingsService
import org.rust.cargo.project.toolwindow.CargoProjectStructure
import org.rust.fileTree
import javax.swing.tree.TreeModel

class CargoProjectStructureTest : RsWithToolchainTestBase() {

    fun `test targets`() = doTest("""
        Root
         Project
          Targets
           Target(foo[lib])
           Target(foo[bin])
           Target(example[example])
           Target(test[test])
           Target(bench[bench])
          Features
           Package(foo-0.1.0)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("main.rs", "")
            rust("lib.rs", "")
        }
        dir("benches") {
            rust("bench.rs", "")
        }
        dir("examples") {
            rust("example.rs", "")
        }
        dir("tests") {
            rust("test.rs", "")
        }
    }

    fun `test workspace members`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(bar-0.1.0)
           Package(foo-0.1.0)
          WorkspaceMember(bar)
           Targets
            Target(bar[bin])
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []

            [workspace]
            members = [
                "inner-project"
            ]
        """)

        dir("src") {
            rust("main.rs", "fn main() {}")
        }
        dir("inner-project") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "0.1.0"
                authors = []
            """)
            dir("src") {
                rust("main.rs", "fn main() {}")
            }
        }
    }

    @MinRustcVersion("1.26.0")
    @CargoFeatures(RustProjectSettingsService.FeaturesSetting.Default, "")
    fun `test features default`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(foo-0.1.0)
            Feature(conditional1,enabled)
            Feature(default,enabled)
            Feature(conditional2,disabled)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
            [features]
            default = ["conditional1"]
            conditional1 = []
            conditional2 = []
        """)

        dir("src") {
            rust("main.rs", "")
        }
    }

    @MinRustcVersion("1.26.0")
    @CargoFeatures(RustProjectSettingsService.FeaturesSetting.NoDefault, "conditional2")
    fun `test features no default`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(foo-0.1.0)
            Feature(conditional2,enabled)
            Feature(conditional1,disabled)
            Feature(default,disabled)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
            [features]
            default = ["conditional1"]
            conditional1 = []
            conditional2 = []
        """)

        dir("src") {
            rust("main.rs", "")
        }
    }

    @MinRustcVersion("1.26.0")
    @CargoFeatures(RustProjectSettingsService.FeaturesSetting.All, "")
    fun `test features all`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(foo-0.1.0)
            Feature(conditional1,enabled)
            Feature(conditional2,enabled)
            Feature(default,enabled)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
            [features]
            default = ["conditional1"]
            conditional1 = []
            conditional2 = []
        """)

        dir("src") {
            rust("main.rs", "")
        }
    }

    @MinRustcVersion("1.26.0")
    @CargoFeatures(RustProjectSettingsService.FeaturesSetting.Default, "conditional2 conditional3")
    fun `test features custom`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(foo-0.1.0)
            Feature(conditional2,enabled)
            Feature(conditional3,enabled)
            Feature(conditional1,disabled)
            Feature(conditional4,disabled)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
            [features]
            conditional1 = []
            conditional2 = []
            conditional3 = []
            conditional4 = []
        """)

        dir("src") {
            rust("main.rs", "")
        }
    }

    @MinRustcVersion("1.26.0")
    fun `test features dependencies`() = doTest("""
        Root
         Project
          Targets
           Target(foo[bin])
          Features
           Package(dep1-0.1.0)
            Feature(default,enabled)
            Feature(dep1conditional1,enabled)
            Feature(dep1conditional2,disabled)
            Feature(dep1conditional3,disabled)
           Package(dep2-0.1.0)
            Feature(dep2conditional2,enabled)
            Feature(dep2conditional3,enabled)
            Feature(default,disabled)
            Feature(dep2conditional1,disabled)
           Package(foo-0.1.0)
            Feature(conditional1,enabled)
            Feature(default,enabled)
            Feature(conditional2,disabled)
    """) {
        toml("Cargo.toml", """
            [package]
            name = "foo"
            version = "0.1.0"
            authors = []
            [features]
            default = ["conditional1"]
            conditional1 = []
            conditional2 = []
            [dependencies]
            dep1 = { path = "./dep1" }
            dep2 = { path = "./dep2", default-features = false, features = ["dep2conditional2", "dep2conditional3"] }
        """)

        dir("src") {
            rust("main.rs", "")
        }

        dir("dep1") {
            toml("Cargo.toml", """
                [package]
                name = "dep1"
                version = "0.1.0"
                authors = []
                [features]
                default = ["dep1conditional1"]
                dep1conditional1 = []
                dep1conditional2 = []
                dep1conditional3 = []
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }
        dir("dep2") {
            toml("Cargo.toml", """
                [package]
                name = "dep2"
                version = "0.1.0"
                authors = []
                [features]
                default = ["dep2conditional1"]
                dep2conditional1 = []
                dep2conditional2 = []
                dep2conditional3 = []
            """)

            dir("src") {
                rust("lib.rs", "")
            }
        }
    }

    private fun doTest(expectedTreeStructure: String, builder: FileTreeBuilder.() -> Unit) {
        fileTree(builder).create()
        val structure = CargoProjectStructure(project.cargoProjects.allProjects.toList())
        assertTreeStructureEquals(structure, expectedTreeStructure.trimIndent() + "\n")
    }

    // TODO: use [com.intellij.testFramework.PlatformTestUtil#assertTreeEqual(javax.swing.JTree, java.lang.String)]
    //  Original [com.intellij.testFramework.PlatformTestUtil#assertTreeStructureEquals] was dropped in
    //  https://github.com/JetBrains/intellij-community/commit/6c92cfb65a482f05a87f5e3990b5635010e787f0
    //  So, it's temporarily solution to allow test works as before
    private fun assertTreeStructureEquals(treeModel: TreeModel, expected: String) {
        val structure = object : AbstractTreeStructure() {
            override fun getRootElement(): Any = treeModel.root
            override fun getChildElements(element: Any): Array<Any> =
                TreeUtil.nodeChildren(element, treeModel).toList().toTypedArray()
            override fun getParentElement(element: Any): Any? = (element as AbstractTreeNode<*>).parent
            override fun createDescriptor(element: Any, parentDescriptor: NodeDescriptor<*>?): NodeDescriptor<*> =
                throw UnsupportedOperationException()
            override fun commit(): Unit = throw UnsupportedOperationException()
            override fun hasSomethingToCommit(): Boolean = throw UnsupportedOperationException()
        }
        assertEquals(expected, PlatformTestUtil.print(structure, treeModel.root, 0, null, -1, ' ', null).toString())
    }
}
