/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.wasmpack.util

import com.intellij.codeInsight.lookup.LookupElementBuilder
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.util.CmdBase
import org.rust.cargo.util.Opt
import org.rust.cargo.util.OptBuilder
import org.rust.cargo.util.RsCommandCompletionProvider

class WasmPackCommandCompletionProvider(
    projects: CargoProjectsService,
    implicitTextPrefix: String,
    workspaceGetter: () -> CargoWorkspace?
) : RsCommandCompletionProvider(projects, implicitTextPrefix, workspaceGetter) {

    constructor(projects: CargoProjectsService, workspaceGetter: () -> CargoWorkspace?)
        : this(projects, "", workspaceGetter)

    override val commonCommands = listOf(
        Cmd("new"),

        Cmd("build") {
            opt("target") {
                listOf("bundler", "nodejs", "web", "no-modules").map {
                    LookupElementBuilder.create(it)
                }
            }

            // Profile
            flag("dev")
            flag("profiling")
            flag("release")

            opt("out-dir") {
                listOf(LookupElementBuilder.create("pkg"))
            }

            opt("out-name") {
                it.projects.map { LookupElementBuilder.create(it.presentableName) }
            }

            opt("scope") {
                listOf(LookupElementBuilder.create("example"))
            }
        },

        Cmd("test") {
            // Profile
            flag("release")

            // Test environment
            flag("headless")
            flag("node")
            flag("firefox")
            flag("chrome")
            flag("safari")
        },

        Cmd("pack"),
        Cmd("publish") {
            opt("tag") {
                listOf(LookupElementBuilder.create("latest"))
            }
        }
    )

    protected class Cmd(
        name: String,
        initOptions: WasmPackOptBuilder.() -> Unit = {}
    ) : CmdBase(name) {
        override val options: List<Opt> = WasmPackOptBuilder().apply(initOptions).result
    }

    protected class WasmPackOptBuilder(override val result: MutableList<Opt> = mutableListOf()) : OptBuilder
}
