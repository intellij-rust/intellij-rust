package org.rust.ide.sdk.remote

import com.intellij.openapi.project.Project

interface RsRemoteFilesChooser {
    fun chooseRemoteFiles(project: Project, data: RsRemoteSdkAdditionalDataBase, foldersOnly: Boolean): Array<String>
}
