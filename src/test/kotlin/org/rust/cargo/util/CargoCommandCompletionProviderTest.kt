/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.completion.PlainPrefixMatcher
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.CargoWorkspaceData
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.RsTestBase
import java.nio.file.Paths


class CargoCommandCompletionProviderTest : RsTestBase() {
    fun `test split context prefix`() {
        val provider = CargoCommandCompletionProvider(project.cargoProjects, null)
        fun doCheck(text: String, ctx: String, prefix: String) {
            val (actualCtx, actualPrefix) = provider.splitContextPrefix(text)
            check(actualCtx == ctx && actualPrefix == prefix) {
                "\nExpected\n\n$ctx, $prefix\nActual:\n$actualCtx, $actualPrefix"
            }
        }

        doCheck("build", "", "build")
        doCheck("b", "", "b")
        doCheck("build ", "build", "")
    }

    fun `test complete empty`() = checkCompletion(
        "",
        listOf("run", "test", "check", "build", "update", "bench", "doc", "publish", "clean", "search", "install")
    )

    fun `test complete command name`() = checkCompletion(
        "b",
        listOf("build", "bench")
    )

    fun `test no completion for unknown command`() = checkCompletion(
        "frob ",
        listOf()
    )

    fun `test complete run args`() = checkCompletion(
        "run ",
        listOf(
            "--release", "--jobs",
            "--features", "--all-features", "--no-default-features",
            "--triple",
            "--verbose", "--quite",
            "--bin", "--example", "--package",
            "--manifest-path"
        )
    )

    fun `test dont suggest a flag twice`() = checkCompletion(
        "run --release --rel",
        listOf()
    )

    fun `test dont suggest after double dash`() = checkCompletion(
        "run -- --rel",
        listOf()
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
        listOf(project.cargoProjects.allProjects.singleOrNull()?.manifest.toString())
    )

    private fun checkCompletion(
        text: String,
        expectedCompletions: List<String>
    ) {

        val provider = CargoCommandCompletionProvider(project.cargoProjects, TEST_WORKSPACE)
        val (ctx, prefix) = provider.splitContextPrefix(text)
        val matcher = PlainPrefixMatcher(prefix)

        val actual = provider.complete(ctx).filter { matcher.isStartMatch(it) }.map { it.lookupString }
        check(actual == expectedCompletions) {
            "\nExpected:\n$expectedCompletions\n\nActual:\n$actual"
        }
    }

    private val TEST_WORKSPACE = run {
        fun target(name: String, kind: CargoWorkspace.TargetKind): CargoWorkspaceData.Target = CargoWorkspaceData.Target(
            crateRootUrl = "/tmp/lib/rs",
            name = name,
            kind = kind
        )

        fun pkg(
            name: String,
            isWorkspaceMember: Boolean,
            targets: List<CargoWorkspaceData.Target>
        ): CargoWorkspaceData.Package = CargoWorkspaceData.Package(
            name = name,
            id = "$name 1.0.0",
            contentRootUrl = "/tmp",
            version = "1.0.0",
            targets = targets,
            source = null,
            origin = if (isWorkspaceMember) PackageOrigin.WORKSPACE else PackageOrigin.DEPENDENCY
        )

        val pkgs = listOf(
            pkg("foo", true, listOf(
                target("foo", CargoWorkspace.TargetKind.LIB),
                target("bar", CargoWorkspace.TargetKind.BIN),
                target("baz", CargoWorkspace.TargetKind.BIN)
            )),
            pkg("quux", false, listOf(target("quux", CargoWorkspace.TargetKind.LIB)))
        )
        CargoWorkspace.deserialize(Paths.get("/my-crate/Cargo.toml"), CargoWorkspaceData(pkgs, dependencies = emptyMap()))
    }
}
