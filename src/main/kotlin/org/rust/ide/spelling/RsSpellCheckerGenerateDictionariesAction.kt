/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.spelling

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.openapi.roots.ModuleRootManager
import org.rust.cargo.project.model.cargoProjects
import org.rust.cargo.project.workspace.PackageOrigin

class RsSpellCheckerGenerateDictionariesAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val module = e.getData(LangDataKeys.MODULE) ?: return
        val contextRootPath = ModuleRootManager.getInstance(module).contentRoots.firstOrNull()?.path ?: return
        val generator = RsSpellCheckerDictionaryGenerator(project, "$contextRootPath/dicts")
        val cargoProject = project.cargoProjects.allProjects.firstOrNull() ?: return
        cargoProject.workspace?.packages.orEmpty()
            .mapNotNull { if (it.origin == PackageOrigin.STDLIB) it.contentRoot else null }
            .forEach { generator.addFolder("rust", it) }
        generator.generate()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.cargoProjects?.hasAtLeastOneValidProject == true
    }
}
