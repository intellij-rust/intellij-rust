/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.coverage.SimpleCoverageAnnotator
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import java.io.File

class RsCoverageAnnotator(project: Project) : SimpleCoverageAnnotator(project) {
    override fun fillInfoForUncoveredFile(file: File): FileCoverageInfo = FileCoverageInfo()

    override fun getLinesCoverageInformationString(info: FileCoverageInfo): String? =
        when {
            info.totalLineCount == 0 -> null
            info.coveredLineCount == 0 -> "no lines covered"
            info.coveredLineCount * 100 < info.totalLineCount -> "<1% lines covered"
            else -> "${calcCoveragePercentage(info)}% lines covered"
        }

    override fun getFilesCoverageInformationString(info: DirCoverageInfo): String? =
        when {
            info.totalFilesCount == 0 -> null
            info.coveredFilesCount == 0 -> "${info.coveredFilesCount} of ${info.totalFilesCount} files covered"
            else -> "${info.coveredFilesCount} of ${info.totalFilesCount} files"
        }

    companion object {
        fun getInstance(project: Project): RsCoverageAnnotator =
            ServiceManager.getService(project, RsCoverageAnnotator::class.java)
    }
}
