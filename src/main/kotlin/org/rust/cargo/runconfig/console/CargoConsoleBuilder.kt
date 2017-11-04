/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.runconfig.console

import com.intellij.execution.filters.TextConsoleBuilderImpl
import com.intellij.execution.ui.ConsoleView
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope


class CargoConsoleBuilder(project: Project, scope: GlobalSearchScope) : TextConsoleBuilderImpl(project, scope) {
    override fun createConsole(): ConsoleView {
        return CargoConsoleView(project, scope, isViewer, true)
    }
}

