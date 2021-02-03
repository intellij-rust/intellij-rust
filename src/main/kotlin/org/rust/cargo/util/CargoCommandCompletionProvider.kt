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
import org.rust.stdext.buildList

class CargoCommandCompletionProvider(
    projects: CargoProjectsService,
    implicitTextPrefix: String,
    workspaceGetter: () -> CargoWorkspace?
) : RsCommandCompletionProvider(projects, implicitTextPrefix, workspaceGetter) {

    constructor(projects: CargoProjectsService, workspaceGetter: () -> CargoWorkspace?)
        : this(projects, "", workspaceGetter)

    constructor(projects: CargoProjectsService, workspace: CargoWorkspace?) : this(projects, { workspace })

    override val commonCommands: List<CmdBase> = buildList {
        for (command in CargoCommands.values()) {
            Cmd(command.presentableName) {
                for (option in command.options) {
                    val completer = getCompleterForOption(option.name)
                    if (completer != null) {
                        opt(option.name, completer)
                    } else {
                        flag(option.name)
                    }
                }
            }.also { add(it) }
        }
    }

    private class Cmd(name: String, initOptions: CargoOptBuilder.() -> Unit = {}) : CmdBase(name) {
        override val options: List<Opt> = CargoOptBuilder().apply(initOptions).result
    }

    private class CargoOptBuilder(override val result: MutableList<Opt> = mutableListOf()) : OptBuilder
}

private fun targetCompleter(kind: CargoWorkspace.TargetKind): ArgCompleter = { ctx ->
    ctx.currentWorkspace?.packages.orEmpty()
        .filter { pkg -> pkg.origin == PackageOrigin.WORKSPACE }
        .flatMap { pkg -> pkg.targets.filter { it.kind == kind } }
        .map { target -> target.lookupElement }
}

private fun getCompleterForOption(name: String): ArgCompleter? = when (name) {
    "bin" -> targetCompleter(CargoWorkspace.TargetKind.Bin)
    "example" -> targetCompleter(CargoWorkspace.TargetKind.ExampleBin)
    "test" -> targetCompleter(CargoWorkspace.TargetKind.Test)
    "bench" -> targetCompleter(CargoWorkspace.TargetKind.Bench)
    "package" -> { ctx -> ctx.currentWorkspace?.packages.orEmpty().map { it.lookupElement } }
    "manifest-path" -> { ctx -> ctx.projects.map { it.lookupElement } }
    "target" -> { ctx -> getTargetTripleCompletions(ctx) }
    else -> null
}

private fun getTargetTripleCompletions(ctx: Context): List<LookupElement> {
    val cargoProject = ctx.projects.firstOrNull() ?: return emptyList()
    val targets = cargoProject.rustcInfo?.targets ?: return emptyList()

    return targets.map { LookupElementBuilder.create(it) }
}

private val CargoProject.lookupElement: LookupElement
    get() = LookupElementBuilder.create(manifest.toString()).withIcon(CargoIcons.ICON)

private val CargoWorkspace.Target.lookupElement: LookupElement
    get() = LookupElementBuilder.create(name)

private val CargoWorkspace.Package.lookupElement: LookupElement
    get() {
        val priority = if (origin == PackageOrigin.WORKSPACE) 1.0 else 0.0
        return LookupElementBuilder.create(name).withPriority(priority)
    }
