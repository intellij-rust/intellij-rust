/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide

import com.intellij.ide.browsers.BrowserLauncher
import com.intellij.ide.browsers.WebBrowser
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.replaceService
import java.io.File
import java.nio.file.Path

class MockBrowserLauncher : BrowserLauncher() {
    private var lastFile: File? = null
    private var lastPath: Path? = null
    var lastUrl: String? = null

    override fun browse(file: File) {
        lastFile = file
    }

    override fun browse(file: Path) {
        lastPath = file
    }

    override fun browse(url: String, browser: WebBrowser?, project: Project?) {
        lastUrl = url
    }

    override fun browseUsingPath(
        url: String?,
        browserPath: String?,
        browser: WebBrowser?,
        project: Project?,
        openInNewWindow: Boolean,
        additionalParameters: Array<String>
    ): Boolean = false

    override fun open(url: String) {}

    fun replaceService(disposable: Disposable) {
        ApplicationManager.getApplication().replaceService(BrowserLauncher::class.java, this, disposable)
    }
}
