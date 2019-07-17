/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.codeEditor.printing.ExportToHTMLSettings
import com.intellij.coverage.*
import com.intellij.coverage.view.CoverageViewExtension
import com.intellij.coverage.view.CoverageViewManager
import com.intellij.coverage.view.DirectoryCoverageViewExtension
import com.intellij.coverage.view.PercentageCoverageColumnInfo
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.coverage.CoverageEnabledConfiguration
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AlphaComparator
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.rt.coverage.data.ClassData
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.ColumnInfo
import org.rust.cargo.runconfig.command.CargoCommandConfiguration
import org.rust.coverage.LcovCoverageReport.Serialization.writeLcov
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsFile
import java.io.File
import java.io.IOException
import java.util.*

class RsCoverageEngine : CoverageEngine() {
    override fun getQualifiedNames(sourceFile: PsiFile): Set<String> {
        val qName = getQName(sourceFile)
        return if (qName != null) setOf(qName) else emptySet()
    }

    override fun acceptedByFilters(psiFile: PsiFile, suite: CoverageSuitesBundle): Boolean = psiFile is RsFile

    override fun coverageEditorHighlightingApplicableTo(psiFile: PsiFile): Boolean = psiFile is RsFile

    override fun createCoverageEnabledConfiguration(conf: RunConfigurationBase<*>?): CoverageEnabledConfiguration =
        RsCoverageEnabledConfiguration(conf)

    override fun getQualifiedName(outputFile: File, sourceFile: PsiFile): String? = getQName(sourceFile)

    override fun includeUntouchedFileInCoverage(
        qualifiedName: String,
        outputFile: File,
        sourceFile: PsiFile,
        suite: CoverageSuitesBundle
    ): Boolean = false

    override fun coverageProjectViewStatisticsApplicableTo(fileOrDir: VirtualFile): Boolean =
        !fileOrDir.isDirectory && fileOrDir.fileType == RsFileType

    override fun getTestMethodName(element: PsiElement, testProxy: AbstractTestProxy): String? = null

    override fun getCoverageAnnotator(project: Project): CoverageAnnotator = RsCoverageAnnotator.getInstance(project)

    override fun isApplicableTo(conf: RunConfigurationBase<*>?): Boolean = conf is CargoCommandConfiguration

    override fun createEmptyCoverageSuite(coverageRunner: CoverageRunner): CoverageSuite = RsCoverageSuite()

    override fun getPresentableText(): String = "Rust Coverage"

    override fun createCoverageViewExtension(
        project: Project,
        suiteBundle: CoverageSuitesBundle,
        stateBean: CoverageViewManager.StateBean
    ): CoverageViewExtension? =
        object : DirectoryCoverageViewExtension(project, getCoverageAnnotator(project), suiteBundle, stateBean) {
            override fun createColumnInfos(): Array<ColumnInfo<NodeDescriptor<Any>, String>> {
                val percentage = PercentageCoverageColumnInfo(
                    1,
                    "Covered, %",
                    mySuitesBundle,
                    myStateBean
                )
                val files = object : ColumnInfo<NodeDescriptor<Any>, String>("File") {
                    override fun valueOf(item: NodeDescriptor<Any>?): String? = item.toString()
                    override fun getComparator(): Comparator<NodeDescriptor<Any>>? = AlphaComparator.INSTANCE
                }
                return arrayOf(files, percentage)
            }

            override fun getChildrenNodes(node: AbstractTreeNode<*>): List<AbstractTreeNode<*>> =
                super.getChildrenNodes(node).filter { child ->
                    val value = child.value
                    if (value is PsiFile) {
                        value.fileType == RsFileType
                    } else {
                        child.name != Project.DIRECTORY_STORE_FOLDER
                    }
                }
        }

    override fun recompileProjectAndRerunAction(
        module: Module,
        suite: CoverageSuitesBundle,
        chooseSuiteAction: Runnable
    ): Boolean = false

    override fun canHavePerTestCoverage(conf: RunConfigurationBase<*>?): Boolean = false

    override fun findTestsByNames(testNames: Array<out String>, project: Project): List<PsiElement> = emptyList()

    override fun isReportGenerationAvailable(
        project: Project,
        dataContext: DataContext,
        currentSuite: CoverageSuitesBundle
    ): Boolean = true

    override fun generateReport(project: Project, dataContext: DataContext, currentSuiteBundle: CoverageSuitesBundle) {
        val coverageReport = LcovCoverageReport()
        val dataManager = CoverageDataManager.getInstance(project)
        for (suite in currentSuiteBundle.suites) {
            val projectData = suite.getCoverageData(dataManager) ?: continue
            val classDataMap = projectData.classes
            for ((filePath, classData) in classDataMap) {
                val lineHitsList = convertClassDataToLineHits(classData)
                coverageReport.mergeFileReport(null, filePath, lineHitsList)
            }
        }

        val settings = ExportToHTMLSettings.getInstance(project)
        val outputDir = File(settings.OUTPUT_DIRECTORY)
        FileUtil.createDirectory(outputDir)
        val outputFileName = getOutputFileName(currentSuiteBundle)
        val title = "Coverage Report Generation"
        try {
            val output = File(outputDir, outputFileName)
            writeLcov(coverageReport, output)
            refresh(output)
            val url = "http://ltp.sourceforge.net/coverage/lcov.php"
            Messages.showInfoMessage(
                "<html>Coverage report has been successfully saved as '$outputFileName' file.<br>" +
                    "Use <a href='$url'>$url</a> to generate HTML output.</html>",
                title
            )
        } catch (e: IOException) {
            LOG.warn("Can not export coverage data", e)
            Messages.showErrorDialog("Can not generate coverage report: ${e.message}", title)
        }
    }

    private fun refresh(file: File) {
        val vFile = VfsUtil.findFileByIoFile(file, true)
        if (vFile != null) {
            runWriteAction { vFile.refresh(false, false) }
        }
    }

    private fun getOutputFileName(currentSuitesBundle: CoverageSuitesBundle): String = buildString {
        for (suite in currentSuitesBundle.suites) {
            val presentableName = suite.presentableName
            append(presentableName)
        }
        append(".lcov")
    }

    private fun convertClassDataToLineHits(classData: ClassData): List<LcovCoverageReport.LineHits> {
        val lineCount = classData.lines.size
        val lineHitsList = ContainerUtil.newArrayListWithCapacity<LcovCoverageReport.LineHits>(lineCount)
        for (lineInd in 0 until lineCount) {
            val lineData = classData.getLineData(lineInd)
            if (lineData != null) {
                val lineHits = LcovCoverageReport.LineHits(lineData.lineNumber, lineData.hits)
                lineHitsList.add(lineHits)
            }
        }
        return lineHitsList
    }

    override fun collectSrcLinesForUntouchedFile(classFile: File, suite: CoverageSuitesBundle): List<Int>? = null

    override fun createCoverageSuite(
        covRunner: CoverageRunner,
        name: String,
        coverageDataFileProvider: CoverageFileProvider,
        filters: Array<out String>?,
        lastCoverageTimeStamp: Long,
        suiteToMerge: String?,
        coverageByTestEnabled: Boolean,
        tracingEnabled: Boolean,
        trackTestFolders: Boolean,
        project: Project?
    ): CoverageSuite? = null

    override fun createCoverageSuite(
        covRunner: CoverageRunner,
        name: String,
        coverageDataFileProvider: CoverageFileProvider,
        config: CoverageEnabledConfiguration
    ): CoverageSuite? {
        if (config !is RsCoverageEnabledConfiguration) return null
        val configuration = config.configuration as? CargoCommandConfiguration ?: return null
        return RsCoverageSuite(
            configuration.project,
            name,
            coverageDataFileProvider,
            covRunner,
            configuration.workingDirectory?.toString(),
            config.coverageProcess
        )
    }

    companion object {
        private val LOG: Logger = Logger.getInstance(RsCoverageEngine::class.java)

        fun getInstance(): RsCoverageEngine = CoverageEngine.EP_NAME.findExtensionOrFail(RsCoverageEngine::class.java)

        private fun getQName(sourceFile: PsiFile): String? = sourceFile.virtualFile?.path
    }
}
