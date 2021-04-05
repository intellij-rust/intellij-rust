/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.TestDataProvider
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.rust.cargo.project.model.CargoProject
import org.rust.cargo.project.model.CargoProjectsService
import org.rust.cargo.project.model.impl.CargoProjectImpl
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.cargo.project.workspace.FeatureName
import org.rust.cargo.project.workspace.FeatureState
import org.rust.lang.core.macros.MACRO_EXPANSION_VFS_ROOT
import org.rust.lang.core.macros.MacroExpansionFileSystem
import org.rust.openapiext.isFeatureEnabled
import org.rust.openapiext.setFeatureEnabled

fun checkMacroExpansionFileSystemAfterTest() {
    val vfs = MacroExpansionFileSystem.getInstance()
    val rootPath = "/$MACRO_EXPANSION_VFS_ROOT"
    if (vfs.exists(rootPath)) {
        val incorrectFilePaths = vfs.getDirectory(rootPath)?.copyChildren().orEmpty()
            .filter { it !is MacroExpansionFileSystem.FSItem.FSDir.DummyDir }
            .map { rootPath + "/" + it.name }

        if (incorrectFilePaths.isNotEmpty()) {
            for (path in incorrectFilePaths) {
                MacroExpansionFileSystem.getInstance().deleteFile(path)
            }
            error("$incorrectFilePaths are not dummy dirs")
        }
    }
    val incorrectFilePaths = vfs.getDirectory("/")?.copyChildren().orEmpty()
        .filter { it.name != MACRO_EXPANSION_VFS_ROOT }
        .map { "/" + it.name }

    if (incorrectFilePaths.isNotEmpty()) {
        for (path in incorrectFilePaths) {
            MacroExpansionFileSystem.getInstance().deleteFile(path)
        }
        error("$incorrectFilePaths should have been removed at the end of the test")
    }
}

fun CargoProjectsService.singleProject(): CargoProjectImpl {
    return when (allProjects.size) {
        0 -> error("No cargo projects found")
        1 -> allProjects.single() as CargoProjectImpl
        else -> error("Expected single cargo project, found multiple: $allProjects")
    }
}

fun CargoProject.workspaceOrFail(): CargoWorkspace {
    return workspace ?: error("Failed to get cargo workspace. Status: $workspaceStatus")
}

fun CargoProjectsService.singleWorkspace(): CargoWorkspace = singleProject().workspaceOrFail()

fun CargoProjectsService.singlePackage(name: String): CargoWorkspace.Package =
    singleWorkspace().packages.singleOrNull { it.name == name } ?: error("Package with name `$name` does not exists")

fun CargoWorkspace.Package.checkFeatureEnabled(featureName: FeatureName) =
    checkFeatureState(featureName, FeatureState.Enabled)

fun CargoWorkspace.Package.checkFeatureDisabled(featureName: FeatureName) =
    checkFeatureState(featureName, FeatureState.Disabled)

fun CargoWorkspace.Package.checkFeatureState(featureName: FeatureName, expectedState: FeatureState) {
    val state = featureState[featureName]
        ?: error("Feature `$featureName` does not exists in package `$name:$version`")
    check(state == expectedState) {
        "Feature `${featureName}` in package `$name:$version` is expected to be ${!state}, but it is $state"
    }
}


fun CodeInsightTestFixture.launchAction(
    actionId: String,
    vararg context: Pair<DataKey<*>, *>,
    shouldBeEnabled: Boolean = true
): Presentation {
    TestApplicationManager.getInstance().setDataProvider(object : TestDataProvider(project) {
        override fun getData(dataId: String): Any? {
            for ((key, value) in context) {
                if (key.`is`(dataId)) return value
            }
            return super.getData(dataId)
        }
    }, testRootDisposable)

    val action = ActionManager.getInstance().getAction(actionId) ?: error("Failed to find action by `$actionId` id")
    val presentation = testAction(action)
    if (shouldBeEnabled) {
        check(presentation.isEnabledAndVisible) { "Failed to run `${action.javaClass.simpleName}` action" }
    } else {
        check(!presentation.isEnabledAndVisible) { "`${action.javaClass.simpleName}` action shouldn't be enabled"}
    }
    return presentation
}

fun <T> withTestDialog(testDialog: TestDialog, action: () -> T): T {
    val oldDialog = TestDialogManager.setTestDialog(testDialog)
    return try {
        action()
    } finally {
        TestDialogManager.setTestDialog(oldDialog)
    }
}

fun setExperimentalFeatureEnabled(featureId: String, enabled: Boolean, disposable: Disposable) {
    val oldValue = isFeatureEnabled(featureId)
    setFeatureEnabled(featureId, enabled)
    Disposer.register(disposable) { setFeatureEnabled(featureId, oldValue) }
}
