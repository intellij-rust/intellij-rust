package org.rust.ide.sdk.add

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.UserDataHolder

interface RsAddSdkProvider {

    fun createView(
        project: Project?,
        module: Module?,
        newProjectPath: String?,
        existingSdks: List<Sdk>,
        context: UserDataHolder
    ): RsAddSdkView?

    companion object {
        val EP_NAME: ExtensionPointName<RsAddSdkProvider> = ExtensionPointName.create("org.rust.rustAddSdkProvider")
    }
}
