/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.intentions

import com.intellij.codeInsight.intention.IntentionActionDelegate
import org.intellij.lang.annotations.Language
import org.rust.FileTree
import org.rust.WithExperimentalFeatures
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.fileTree
import org.rust.ide.experiments.RsExperiments
import org.rust.toml.crates.local.CargoRegistryCrate
import org.rust.toml.crates.local.CargoRegistryCrateVersion
import org.rust.toml.crates.local.withMockedCrates

@WithExperimentalFeatures(RsExperiments.CRATES_LOCAL_INDEX)
class AddCrateDependencyIntentionTest : RsWithToolchainTestBase() {
    fun `test unavailable if crate is not found`() = doUnavailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    })

    fun `test unavailable if import is resolved`() = doUnavailableTest(fileTree {
        toml(
            "Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"
        """
        )

        dir("src") {
            rust(
                "lib.rs", """
                mod foo;

                use foo/*caret*/::S;
            """
            )
            rust("foo.rs", """pub struct S;""")
        }
    }, "foo" to CargoRegistryCrate.of("1"))

    fun `test add crate from use item`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test add crate from extern crate item`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                extern crate foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []

        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test add last version`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        foo = "3"
    """, "foo" to CargoRegistryCrate.of("1", "2", "3"))

    fun `test ignore yanked versions`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate(listOf(
        CargoRegistryCrateVersion("1", false, listOf()),
        CargoRegistryCrateVersion("1.1", true, listOf()),
        CargoRegistryCrateVersion("1.2", true, listOf())
    )))

    fun `test create dependencies if missing`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test append to existing dependencies`() = doAvailableTest(fileTree {
        toml("Cargo.toml", """
            [package]
            name = "bar"
            version = "0.1.0"
            authors = []
            edition = "2018"

            [dependencies]
            log = "0.4"
        """)

        dir("src") {
            rust("lib.rs", """
                use foo/*caret*/;
            """)
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        log = "0.4"
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    fun `test workspace`() = doAvailableTest("bar", fileTree {
        toml("Cargo.toml", """
            [workspace]
            members = ["bar"]
        """)
        dir("bar") {
            toml("Cargo.toml", """
                [package]
                name = "bar"
                version = "0.1.0"
                authors = []
                edition = "2018"

                [dependencies]
            """)

            dir("src") {
                rust("lib.rs", """
                    use foo/*caret*/;
                """)
            }
        }
    }, """
        [package]
        name = "bar"
        version = "0.1.0"
        authors = []
        edition = "2018"

        [dependencies]
        foo = "1"
    """, "foo" to CargoRegistryCrate.of("1"))

    private fun doUnavailableTest(
        fileTree: FileTree,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        doTest(crates.toList()) {
            val project = fileTree.create()
            myFixture.configureFromExistingVirtualFile(project.file("src/lib.rs"))
            val intention = myFixture.availableIntentions.firstOrNull {
                val originalIntention = IntentionActionDelegate.unwrap(it)
                AddCrateDependencyIntention::class == originalIntention::class
            }
            assertNull(intention)
        }
    }

    private fun doAvailableTest(
        directory: String,
        fileTree: FileTree,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) {
        doTest(crates.toList()) {
            val project = fileTree.create()
            myFixture.configureFromExistingVirtualFile(project.file("${directory}/src/lib.rs"))
            val intention = myFixture.availableIntentions.firstOrNull {
                val originalIntention = IntentionActionDelegate.unwrap(it)
                AddCrateDependencyIntention::class == originalIntention::class
            }!!
            myFixture.launchAction(intention)
            myFixture.openFileInEditor(project.file("${directory}/Cargo.toml"))
            myFixture.checkResult(after.trimIndent())
        }
    }

    private fun doAvailableTest(
        fileTree: FileTree,
        @Language("TOML") after: String,
        vararg crates: Pair<String, CargoRegistryCrate>
    ) = doAvailableTest(".", fileTree, after, *crates)

    private fun doTest(crates: List<Pair<String, CargoRegistryCrate>>, action: () -> Unit) {
        withMockedCrates(crates.toMap()) {
            action()
        }
    }
}
