/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.CoverageEngine
import com.intellij.coverage.CoverageRunner
import com.intellij.coverage.CoverageSuite
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapiext.isDispatchThread
import com.intellij.rt.coverage.data.LineData
import com.intellij.rt.coverage.data.ProjectData
import org.rust.coverage.LcovCoverageReport.Serialization.readLcov
import org.rust.openapiext.computeWithCancelableProgress
import java.io.File
import java.io.IOException

class RsCoverageRunner : CoverageRunner() {
    override fun getPresentableName(): String = "Rust"

    override fun getDataFileExtension(): String = "info"

    override fun getId(): String = "RsCoverageRunner"

    override fun acceptsCoverageEngine(engine: CoverageEngine): Boolean = engine is RsCoverageEngine

    override fun loadCoverageData(sessionDataFile: File, baseCoverageSuite: CoverageSuite?): ProjectData? {
        if (baseCoverageSuite !is RsCoverageSuite) return null
        return try {
            if (isDispatchThread) {
                baseCoverageSuite.project.computeWithCancelableProgress("Loading Coverage Data...") {
                    readProjectData(sessionDataFile, baseCoverageSuite)
                }
            } else {
                readProjectData(sessionDataFile, baseCoverageSuite)
            }
        } catch (e: IOException) {
            LOG.warn("Can't read coverage data", e)
            null
        }
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RsCoverageRunner::class.java)

        @Throws(IOException::class)
        private fun readProjectData(dataFile: File, coverageSuite: RsCoverageSuite): ProjectData? {
            val coverageProcess = coverageSuite.coverageProcess
            // coverageProcess == null means that we are switching to data gathered earlier
            if (coverageProcess != null) {
                repeat(100) {
                    ProgressManager.checkCanceled()
                    if (coverageProcess.waitFor(100)) return@repeat
                }

                if (!coverageProcess.isProcessTerminated) {
                    coverageProcess.destroyProcess()
                    return null
                }
            }

            val projectData = ProjectData()
            val report = readLcov(dataFile, coverageSuite.contextFilePath)
            for ((filePath, lineHitsList) in report.records) {
                val classData = projectData.getOrCreateClassData(filePath)
                val max = lineHitsList.lastOrNull()?.lineNumber ?: 0
                val lines = arrayOfNulls<LineData>(max + 1)
                for (lineHits in lineHitsList) {
                    val lineData = LineData(lineHits.lineNumber, null)
                    lineData.hits = lineHits.hits
                    lines[lineHits.lineNumber] = lineData
                }
                classData.setLines(lines)
            }
            return projectData
        }
    }
}
