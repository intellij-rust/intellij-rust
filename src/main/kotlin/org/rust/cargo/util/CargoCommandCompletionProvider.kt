/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.util

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.cargo.icons.CargoIcons
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.PackageOrigin
import org.rust.lang.core.completion.withPriority

class CargoCommandCompletionProvider(
    projects: CargoProjectsService,
    implicitTextPrefix: String,
    workspaceGetter: () -> CargoWorkspace?
) : RsCommandCompletionProvider(projects, implicitTextPrefix, workspaceGetter) {

    constructor(projects: CargoProjectsService, workspaceGetter: () -> CargoWorkspace?)
        : this(projects, "", workspaceGetter)

    constructor(projects: CargoProjectsService, workspace: CargoWorkspace?) : this(projects, { workspace })

    override val commonCommands = listOf(
        Cmd("run") {
            compileOptions()
            targetBin()
            targetExample()
            pkg()
            manifestPath()
        },

        Cmd("test") {
            compileOptions()
            targetAll()
            flag("doc")
            pkgAll()
            manifestPath()
            flag("no-run")
            flag("no-fail-fast")
        },

        Cmd("check") {
            compileOptions() // yeah, you can check with `--release`
            targetAll()
            pkgAll()
            manifestPath()
        },

        Cmd("build") {
            compileOptions()
            targetAll()
            pkgAll()
            manifestPath()
        },

        Cmd("update") {
            pkg()
            manifestPath()
            flag("aggressive")
            flag("precise")
        },

        Cmd("bench") {
            compileOptions()
            targetAll()
            pkgAll()
            manifestPath()
        },

        Cmd("doc") {
            compileOptions()
            pkgAll()
            manifestPath()
            targetAll()
        },

        Cmd("publish") {
            flag("index")
            flag("token")
            flag("no-verify")
            flag("allow-dirty")
            flag("jobs")
            flag("dry-run")
            manifestPath()
        },

        Cmd("clean") {
            compileOptions()
            pkg()
            manifestPath()
        },

        Cmd("search") {
            flag("index")
            flag("limit")
        },

        Cmd("install") {
            compileOptions()
            targetAll()
            flag("root")
            flag("force")
        }
    )

    protected class Cmd(
        name: String,
        initOptions: CargoOptBuilder.() -> Unit = {}
    ) : CmdBase(name) {
        override val options: List<Opt> = CargoOptBuilder().apply(initOptions).result
    }

    protected class CargoOptBuilder(
        override val result: MutableList<Opt> = mutableListOf()
    ) : OptBuilder {
        fun compileOptions() {
            release()
            jobs()
            features()
            verbose()
        }

        fun release() = flag("release")
        fun jobs() = flag("jobs")
        fun features() {
            flag("features")
            flag("all-features")
            flag("no-default-features")
        }

        private fun targetCompleter(kind: CargoWorkspace.TargetKind): ArgCompleter = { ctx ->
            ctx.currentWorkspace?.packages.orEmpty()
                .filter { it.origin == PackageOrigin.WORKSPACE }
                .flatMap { it.targets.filter { it.kind == kind } }
                .map { it.lookupElement }
        }

        fun targetBin() = opt("bin", targetCompleter(CargoWorkspace.TargetKind.Bin))

        fun targetExample() = opt("example", targetCompleter(CargoWorkspace.TargetKind.ExampleBin))
        fun targetTest() = opt("test", targetCompleter(CargoWorkspace.TargetKind.Test))
        fun targetBench() = opt("bench", targetCompleter(CargoWorkspace.TargetKind.Bench))

        fun targetAll() {
            flag("lib")

            targetBin()
            flag("bins")

            targetExample()
            flag("examples")

            targetTest()
            flag("tests")

            targetBench()
            flag("bench")
        }

        fun pkg() = opt("package") { ctx ->
            ctx.currentWorkspace?.packages.orEmpty().map { it.lookupElement }
        }

        fun pkgAll() {
            pkg()
            flag("all")
            flag("exclude")
        }

        fun manifestPath() {
            opt("manifest-path") { ctx ->
                ctx.projects.map { it.lookupElement }
            }
        }

        fun verbose() {
            flag("verbose")
            flag("quiet")
        }
    }
}

private val CargoProject.lookupElement: LookupElement
    get() =
        LookupElementBuilder
            .create(manifest.toString())
            .withIcon(CargoIcons.ICON)

private val CargoWorkspace.Target.lookupElement: LookupElement get() = LookupElementBuilder.create(name)
private val CargoWorkspace.Package.lookupElement: LookupElement
    get() {
        val priority = if (origin == PackageOrigin.WORKSPACE) 1.0 else 0.0
        return LookupElementBuilder.create(name).withPriority(priority)
    }
