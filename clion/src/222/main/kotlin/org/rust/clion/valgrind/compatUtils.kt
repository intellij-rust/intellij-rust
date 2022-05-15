/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.clion.valgrind

import com.intellij.openapi.project.Project
import com.jetbrains.cidr.cpp.profiling.MemoryProfileTreeDataModel
import com.jetbrains.cidr.cpp.profiling.ui.MemoryProfileOutputPanel
import com.jetbrains.cidr.cpp.valgrind.ValgrindUtil
import com.jetbrains.cidr.cpp.valgrind.actions.EditValgrindSettingsAction

fun createMemoryProfileOutputPanel(treeDataModel: MemoryProfileTreeDataModel, project: Project): MemoryProfileOutputPanel =
    MemoryProfileOutputPanel(treeDataModel, EditValgrindSettingsAction(), ValgrindUtil.TREE_POPUP_ID, project)

