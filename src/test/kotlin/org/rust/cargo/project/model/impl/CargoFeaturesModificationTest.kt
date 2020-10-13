/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import org.intellij.lang.annotations.Language
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.FeatureState
import org.rust.cargo.project.workspace.PackageFeature
import org.rust.cargo.project.workspace.PackageOrigin

/**
 * Mostly tests [org.rust.cargo.project.model.impl.CargoProjectsServiceImpl.modifyFeatures] and
 * [org.rust.cargo.project.workspace.WorkspaceImpl.inferFeatureState]
 */
class CargoFeaturesModificationTest : RsWithToolchainTestBase() {
    fun `test independent features`() = doTest("""
        foo = [] # [x]
        bar = [] # [x]
    """, disable("foo", """
        foo = [] # [ ]
        bar = [] # [x]
    """), disable("bar", """
        foo = [] # [ ]
        bar = [] # [ ]
    """), enable("foo", """
        foo = [] # [x]
        bar = [] # [ ]
    """), enable("bar", """
        foo = [] # [x]
        bar = [] # [x]
    """))

    fun `test 2 dependent features`() = doTest("""
        foo = ["dep"] # [x]
        dep = []      # [x]
    """, disable("dep", """
        foo = ["dep"] # [ ]
        dep = []      # [ ]
    """), enable("foo", """
        foo = ["dep"] # [x]
        dep = []      # [x]
    """), disable("foo", """
        foo = ["dep"] # [ ]
        dep = []      # [x]
    """), disable("dep", """
        foo = ["dep"] # [ ]
        dep = []      # [ ]
    """), enable("dep", """
        foo = ["dep"] # [ ]
        dep = []      # [x]
    """))

    fun `test 3 dependent features`() = doTest("""
        foo = ["dep"] # [x]
        bar = ["dep"] # [x]
        dep = []      # [x]
    """, disable("dep", """
        foo = ["dep"] # [ ]
        bar = ["dep"] # [ ]
        dep = []      # [ ]
    """), enable("foo", """
        foo = ["dep"] # [x]
        bar = ["dep"] # [ ]
        dep = []      # [x]
    """), enable("bar", """
        foo = ["dep"] # [x]
        bar = ["dep"] # [x]
        dep = []      # [x]
    """), disable("foo", """
        foo = ["dep"] # [ ]
        bar = ["dep"] # [x]
        dep = []      # [x]
    """), disable("bar", """
        foo = ["dep"] # [ ]
        bar = ["dep"] # [ ]
        dep = []      # [x]
    """), disable("dep", """
        foo = ["dep"] # [ ]
        bar = ["dep"] # [ ]
        dep = []      # [ ]
    """), enable("dep", """
        foo = ["dep"] # [ ]
        bar = ["dep"] # [ ]
        dep = []      # [x]
    """))

    private fun doTest(@Language("TOML") toml: String, vararg checkingSteps: CheckingStep) {
        buildProject {
            toml("Cargo.toml", """
                [package]
                name = "intellij-rust-test"
                version = "0.1.0"
                authors = []

                [features]
                $toml
            """)
            dir("src") {
                rust("lib.rs", "")
            }
        }

        val tomlTrimmed = toml.trimIndent()
        val baseFeatures = tomlTrimmed.lines().map {
            val line = it.removeAfter("#")
            val feature = it.removeAfter("=").trim()
            feature to line
        }

        var cargoProject = project.cargoProjects.allProjects.singleOrNull() ?: error("Cargo project is not created")
        val workspace = cargoProject.workspace ?: error("Cargo workspace is not created")
        var pkg = workspace.packages.single { it.origin == PackageOrigin.WORKSPACE }

        assertEquals(tomlTrimmed, baseFeatures.withState(pkg.featureState))

        for ((i, action) in checkingSteps.withIndex()) {
            project.cargoProjects.modifyFeatures(cargoProject, setOf(PackageFeature(pkg, action.feature)), action.action)

            cargoProject = project.cargoProjects.allProjects.singleOrNull() ?: error("Cargo project is not created")
            pkg = cargoProject.workspace!!.packages.find { it.rootDirectory == pkg.rootDirectory }!!

            assertEquals("${i + 1}th iteration, just ${action.action.toString().toLowerCase()} `${action.feature}` ",
                action.result.trimIndent(),
                baseFeatures.withState(pkg.featureState)
            )
        }
    }

    private fun enable(
        @Language("TOML", suffix = "=1") feature: String,
        @Language("TOML") result: String
    ): CheckingStep = CheckingStep(feature, FeatureState.Enabled, result)

    private fun disable(
        @Language("TOML", suffix = "=1") feature: String,
        @Language("TOML") result: String
    ): CheckingStep = CheckingStep(feature, FeatureState.Disabled, result)

    private data class CheckingStep(val feature: String, val action: FeatureState, val result: String)
}

private fun List<Pair<String, String>>.withState(featureState: Map<String, FeatureState>): String {
    return joinToString(separator = "\n") {
        val state = featureState[it.first] ?: error("Feature `${it.first}` not found")
        val sign = if (state.isEnabled) "x" else " "
        it.second + "# [$sign]"
    }
}

private fun String.removeAfter(s: String): String {
    val index = indexOf(s).takeIf { it != -1 } ?: return this
    return substring(0, index)
}
