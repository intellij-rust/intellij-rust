/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import org.rust.RsTestBase
import org.rust.cargo.CfgOptions
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspace.*
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.singleProject
import java.nio.file.Paths

class CargoCommandCompletionProviderTest : RsTestBase() {
    fun `test split context prefix`() {
        val provider = CargoCommandCompletionProvider(project.cargoProjects, null)
        fun doCheck(text: String, ctx: String, prefix: String) {
            val (actualCtx, actualPrefix) = provider.splitContextPrefix(text)
            assertEquals(ctx, actualCtx)
            assertEquals(prefix, actualPrefix)
        }

        doCheck("build", "", "build")
        doCheck("b", "", "b")
        doCheck("build ", "build", "")
    }

    fun `test complete empty`() = checkCompletion(
        "",
        listOf(
            "bench",
            "build",
            "check",
            "clean",
            "doc",
            "fetch",
            "fix",
            "run",
            "rustc",
            "rustdoc",
            "test",
            "generate-lockfile",
            "locate-project",
            "metadata",
            "pkgid",
            "tree",
            "update",
            "vendor",
            "verify-project",
            "init",
            "install",
            "new",
            "search",
            "uninstall",
            "login",
            "owner",
            "package",
            "publish",
            "yank",
            "help",
            "version"
        )
    )

    fun `test complete command name`() = checkCompletion(
        "b",
        listOf("bench", "build")
    )

    fun `test no completion for unknown command`() = checkCompletion(
        "frob ",
        emptyList()
    )

    fun `test complete run args`() = checkCompletion(
        "run ",
        listOf(
            "--package",
            "--bin",
            "--example",
            "--features",
            "--all-features",
            "--no-default-features",
            "--target",
            "--release",
            "--target-dir",
            "--verbose",
            "--quiet",
            "--color",
            "--message-format",
            "--manifest-path",
            "--frozen",
            "--locked",
            "--offline",
            "--help",
            "--jobs"
        )
    )

    fun `test dont suggest a flag twice`() = checkCompletion(
        "run --release --rel",
        emptyList()
    )

    fun `test dont suggest after double dash`() = checkCompletion(
        "run -- --rel",
        emptyList()
    )

    fun `test suggest package argument`() = checkCompletion(
        "test --package ",
        listOf("foo", "quux")
    )

    fun `test suggest bin argument`() = checkCompletion(
        "run --bin ",
        listOf("bar", "baz")
    )

    fun `test suggest manifest path`() = checkCompletion(
        "run --manifest-path ",
        listOf(project.cargoProjects.singleProject().manifest.toString())
    )

    private fun checkCompletion(
        text: String,
        expectedCompletions: List<String>
    ) {
        val actual = complete(text, project.cargoProjects, testWorkspace)

        check(actual == expectedCompletions) {
            "\nExpected:\n$expectedCompletions\n\nActual:\n$actual"
        }
    }

    private val testWorkspace: CargoWorkspace = run {
        fun target(
            name: String,
            kind: TargetKind,
            edition: Edition = Edition.EDITION_2015
        ): CargoWorkspaceData.Target = CargoWorkspaceData.Target(
            crateRootUrl = "/tmp/lib/rs",
            name = name,
            kind = kind,
            edition = edition,
            doctest = true,
            requiredFeatures = emptyList()
        )

        fun pkg(
            name: String,
            isWorkspaceMember: Boolean,
            targets: List<CargoWorkspaceData.Target>,
            edition: Edition = Edition.EDITION_2015
        ): CargoWorkspaceData.Package = CargoWorkspaceData.Package(
            name = name,
            id = "$name 1.0.0",
            contentRootUrl = "/tmp",
            version = "1.0.0",
            targets = targets,
            source = null,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.DEPENDENCY,
            edition = edition,
            features = emptyMap(),
            enabledFeatures = emptySet(),
            cfgOptions = CfgOptions.EMPTY,
            env = emptyMap(),
            outDirUrl = null
        )

        val pkgs = listOf(
            pkg("foo", true, listOf(
                target("foo", TargetKind.Lib(LibKind.LIB)),
                target("bar", TargetKind.Bin),
                target("baz", TargetKind.Bin)
            )),
            pkg("quux", false, listOf(target("quux", TargetKind.Lib(LibKind.LIB))))
        )
        CargoWorkspace.deserialize(
            Paths.get("/my-crate/Cargo.toml"),
            CargoWorkspaceData(
                pkgs,
                dependencies = emptyMap(),
                rawDependencies = emptyMap()
            ),
            CfgOptions.DEFAULT
        )
    }

    companion object {
        fun complete(text: String, cargoProjects: CargoProjectsService, testWorkspace: CargoWorkspace?): List<String> {
            val provider = CargoCommandCompletionProvider(cargoProjects, testWorkspace)
            val (ctx, prefix) = provider.splitContextPrefix(text)
            val matcher = PlainPrefixMatcher(prefix)

            return provider.complete(ctx).filter { matcher.isStartMatch(it) }.map { it.lookupString }
        }
    }
}
