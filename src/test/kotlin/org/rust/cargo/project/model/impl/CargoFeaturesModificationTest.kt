/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.project.model.impl

import com.intellij.openapi.project.Project
import org.intellij.lang.annotations.Language
import org.rust.*
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.*

/**
 * Mostly tests [org.rust.cargo.project.model.impl.CargoProjectsServiceImpl.modifyFeatures] and
 * [org.rust.cargo.project.workspace.WorkspaceImpl.inferFeatureState]
 */
class CargoFeaturesModificationTest : RsTestBase() {
    @MockCargoFeatures("foo", "bar")
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

    @MockCargoFeatures("foo=[dep]", "dep")
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

    @MockCargoFeatures("foo=[dep]", "bar=[dep]", "dep")
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

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockCargoFeatures("test-package/foo=[dep-lib-2/dep]", "dep-lib-2/dep")
    fun `test external dependency 2 dependent features`() = doTestByTree("""
    #- test-package
        foo = ["dep-lib-2/dep"] # [x]
    #- dep-lib-2
        dep = []                # [x]
    """, disable("foo", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [ ]
    #- dep-lib-2
        dep = []                # [ ]
    """), enable("foo", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [x]
    #- dep-lib-2
        dep = []                # [x]
    """))

    @ProjectDescriptor(WithDependencyRustProjectDescriptor::class)
    @MockCargoFeatures("test-package/foo=[dep-lib-2/dep]", "test-package/bar=[dep-lib-2/dep]", "dep-lib-2/dep")
    fun `test external dependency 3 dependent features`() = doTestByTree("""
    #- test-package
        foo = ["dep-lib-2/dep"] # [x]
        bar = ["dep-lib-2/dep"] # [x]
    #- dep-lib-2
        dep = []                # [x]
    """, disable("foo", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [ ]
        bar = ["dep-lib-2/dep"] # [x]
    #- dep-lib-2
        dep = []                # [x]
    """), disable("bar", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [ ]
        bar = ["dep-lib-2/dep"] # [ ]
    #- dep-lib-2
        dep = []                # [ ]
    """), enable("foo", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [x]
        bar = ["dep-lib-2/dep"] # [ ]
    #- dep-lib-2
        dep = []                # [x]
    """), enable("bar", """
    #- test-package
        foo = ["dep-lib-2/dep"] # [x]
        bar = ["dep-lib-2/dep"] # [x]
    #- dep-lib-2
        dep = []                # [x]
    """))

    private fun doTest(@Language("TOML") toml: String, vararg checkingSteps: CheckingStep) {
        val tomlTrimmed = toml.trimIndent()
        val baseFeatures = tomlTrimmed.lines().map {
            val line = it.removeAfter("#")
            val feature = it.removeAfter("=").trim()
            feature to line
        }

        var pfs = ProjectPackageFeatureState.capture(project)
        assertEquals(tomlTrimmed, baseFeatures.withState(pfs.pkg.featureState))

        for ((i, action) in checkingSteps.withIndex()) {
            project.cargoProjects.modifyFeatures(pfs.cargoProject, setOf(PackageFeature(pfs.pkg, action.feature)), action.action)
            pfs = ProjectPackageFeatureState.capture(project)
            assertEquals("${i + 1}th iteration, just ${action.action.toString().toLowerCase()} `${action.feature}` ",
                action.result.trimIndent(),
                baseFeatures.withState(pfs.pkg.featureState)
            )
        }
    }

    private fun doTestByTree(@Language("TOML") treeToml: String, vararg checkingSteps: CheckingStep) {
        val tomlTrimmed = treeToml.trimIndent()
        val tree = fileTreeFromText(tomlTrimmed, "#")
        val baseFeatures =  tree.rootDirectory.children.entries.associate { (name, value) ->
            val toml = (value as Entry.File).text!!.trimIndent()
            val baseFeatures = toml.lines().map {
                val line = it.removeAfter("#")
                val feature = it.removeAfter("=").trim()
                feature to line
            }
            name to baseFeatures
        }

        var pfs = ProjectPackageFeatureState.capture(project)
        assertEquals(tomlTrimmed, baseFeatures.withState(pfs.featureState))

        for ((i, action) in checkingSteps.withIndex()) {
            project.cargoProjects.modifyFeatures(pfs.cargoProject, setOf(PackageFeature(pfs.pkg, action.feature)), action.action)
            pfs = ProjectPackageFeatureState.capture(project)
            assertEquals("${i + 1}th iteration, just ${action.action.toString().toLowerCase()} `${action.feature}` ",
                action.result.trimIndent(),
                baseFeatures.withState(pfs.featureState)
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

    private data class ProjectPackageFeatureState(
        var cargoProject: CargoProject,
        var pkg: CargoWorkspace.Package,
        var featureState: Map<String, Map<FeatureName, FeatureState>>,
    ) {
        companion object {
            fun capture(project: Project): ProjectPackageFeatureState {
                val cargoProject = project.cargoProjects.singleProject()
                val pkg = cargoProject.workspaceOrFail().packages.single { it.origin == PackageOrigin.WORKSPACE }
                val featureState = cargoProject.workspaceOrFail().packages
                    .filter { it.origin == PackageOrigin.WORKSPACE || it.origin == PackageOrigin.DEPENDENCY }
                    .associate { it.name to it.featureState }

                return ProjectPackageFeatureState(cargoProject, pkg, featureState)
            }
        }
    }
}

private fun List<Pair<String, String>>.withState(featureState: Map<String, FeatureState>): String {
    return joinToString(separator = "\n") {
        val state = featureState[it.first] ?: error("Feature `${it.first}` not found")
        val sign = if (state.isEnabled) "x" else " "
        it.second + "# [$sign]"
    }
}

private fun Map<String, List<Pair<String, String>>>.withState(featureState: Map<String, Map<String, FeatureState>>): String {
    return entries.joinToString(separator = "\n") { (name, value) ->
        "#- $name\n" + value.withState(featureState[name].orEmpty()).prependIndent("    ")
    }
}

private fun String.removeAfter(s: String): String {
    val index = indexOf(s).takeIf { it != -1 } ?: return this
    return substring(0, index)
}
