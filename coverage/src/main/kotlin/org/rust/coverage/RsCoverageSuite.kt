/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.BaseCoverageSuite
import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageFileProvider
import com.intellij.coverage.CoverageRunner
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.project.Project
import org.jdom.Element

class RsCoverageSuite : BaseCoverageSuite {
    var contextFilePath: String? private set
    val coverageProcess: ProcessHandler?

    constructor() : super() {
        contextFilePath = null
        coverageProcess = null
    }

    constructor(
        project: Project,
        name: String,
        fileProvider: CoverageFileProvider,
        coverageRunner: CoverageRunner,
        contextFilePath: String?,
        coverageProcess: ProcessHandler?
    ) : super(name, fileProvider, System.currentTimeMillis(), false, false, false, coverageRunner, project) {
        this.contextFilePath = contextFilePath
        this.coverageProcess = coverageProcess
    }

    override fun getCoverageEngine(): CoverageEngine = RsCoverageEngine.getInstance()

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        element.setAttribute(CONTEXT_FILE_PATH, contextFilePath ?: return)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        contextFilePath = element.getAttributeValue(CONTEXT_FILE_PATH) ?: return
    }

    companion object {
        private const val CONTEXT_FILE_PATH: String = "CONTEXT_FILE_PATH"
    }
}
