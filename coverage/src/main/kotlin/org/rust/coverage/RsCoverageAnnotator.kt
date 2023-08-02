/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.rust.RsBundle
import java.io.File

class RsCoverageAnnotator(project: Project) : SimpleCoverageAnnotator(project) {
    override fun fillInfoForUncoveredFile(file: File): FileCoverageInfo = FileCoverageInfo()

    override fun getLinesCoverageInformationString(info: FileCoverageInfo): String? =
        when {
            info.totalLineCount == 0 -> null
            info.coveredLineCount == 0 -> RsBundle.message("no.lines.covered")
            info.coveredLineCount * 100 < info.totalLineCount -> RsBundle.message("1.lines.covered")
            else -> RsBundle.message("0.lines.covered", calcCoveragePercentage(info))
        }

    override fun getFilesCoverageInformationString(info: DirCoverageInfo): String? =
        when {
            info.totalFilesCount == 0 -> null
            info.coveredFilesCount == 0 -> RsBundle.message("0.of.1.files.covered", info.coveredFilesCount, info.totalFilesCount)
            else -> RsBundle.message("0.of.1.files", info.coveredFilesCount, info.totalFilesCount)
        }

    companion object {
        fun getInstance(project: Project): RsCoverageAnnotator = project.service()
    }
}
