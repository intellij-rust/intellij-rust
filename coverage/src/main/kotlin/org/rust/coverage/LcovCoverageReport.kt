/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.coverage

import com.intellij.util.containers.PeekableIteratorWrapper
import org.rust.stdext.buildList
import java.io.File
import java.io.PrintWriter

class LcovCoverageReport {
    private val info: MutableMap<String, List<LineHits>> = hashMapOf()
    val records: Set<Map.Entry<String, List<LineHits>>> get() = info.entries

    fun mergeFileReport(basePath: String?, filePath: String, report: List<LineHits>) {
        val file = File(filePath).let {
            if (it.isAbsolute || basePath == null) it else File(basePath, filePath)
        }
        val normalizedFilePath = getNormalizedPath(file)
        val oldReport = info[normalizedFilePath]
        val result = normalizeLineHitsList(report).let {
            if (oldReport == null) it else doMerge(oldReport, report)
        }
        info[normalizedFilePath] = result
    }

    class LineHits(val lineNumber: Int, hits: Int) {
        var hits: Int
            private set

        init {
            this.hits = hits
        }

        fun addHits(hitCount: Int) {
            hits += hitCount
        }
    }

    object Serialization {
        private const val SOURCE_FILE_PREFIX: String = "SF:"
        private const val LINE_HIT_PREFIX: String = "DA:"
        private const val END_OF_RECORD: String = "end_of_record"

        fun readLcov(lcovFile: File, localBaseDir: String? = null): LcovCoverageReport {
            val report = LcovCoverageReport()
            var currentFileName: String? = null
            var lineDataList: MutableList<LineHits>? = null
            lcovFile.forEachLine { line ->
                when {
                    line.startsWith(SOURCE_FILE_PREFIX) -> {
                        currentFileName = line.substring(SOURCE_FILE_PREFIX.length)
                        lineDataList = mutableListOf()
                    }
                    line.startsWith(LINE_HIT_PREFIX) -> {
                        checkNotNull(lineDataList)
                        val values = line
                            .substring(LINE_HIT_PREFIX.length)
                            .split(",")
                            .dropLastWhile { it.isEmpty() }
                        check(values.size == 2)
                        val lineNum = Integer.parseInt(values[0])
                        val hitCount = Integer.parseInt(values[1])
                        val lineHits = LineHits(lineNum, hitCount)
                        lineDataList?.add(lineHits)
                    }
                    END_OF_RECORD == line -> {
                        report.mergeFileReport(
                            localBaseDir,
                            checkNotNull(currentFileName),
                            checkNotNull(lineDataList)
                        )
                        currentFileName = null
                        lineDataList = null
                    }
                }
            }
            check(lineDataList == null)
            return report
        }

        fun writeLcov(report: LcovCoverageReport, outputFile: File) {
            PrintWriter(outputFile).use { out ->
                for ((filePath, fileLineHits) in report.info) {
                    out.print(SOURCE_FILE_PREFIX)
                    out.println(filePath)
                    for (lineHits in fileLineHits) {
                        out.print(LINE_HIT_PREFIX)
                        out.print(lineHits.lineNumber)
                        out.print(',')
                        out.println(lineHits.hits)
                    }
                    out.println(END_OF_RECORD)
                }
            }
        }
    }

    companion object {
        private const val UNIX_SEPARATOR: String = "/"

        fun getNormalizedPath(file: File): String {
            val uri = file.toURI()
            val normalizedUri = uri.normalize()
            var normalizedPath = normalizedUri.path
            if (normalizedPath.startsWith(UNIX_SEPARATOR) && File.separator != UNIX_SEPARATOR) {
                normalizedPath = normalizedPath.substring(1)
            }
            return normalizedPath
        }

        private fun normalizeLineHitsList(lineHits: List<LineHits>): List<LineHits> =
            lineHits.sortedBy { it.lineNumber }.distinctBy { it.lineNumber }

        private fun doMerge(list1: List<LineHits>, list2: List<LineHits>): List<LineHits> = buildList {
            val iter1 = PeekableIteratorWrapper(list1.iterator())
            val iter2 = PeekableIteratorWrapper(list2.iterator())
            while (iter1.hasNext() && iter2.hasNext()) {
                val head1 = iter1.peek()
                val head2 = iter2.peek()
                val next = when {
                    head1.lineNumber < head2.lineNumber ->
                        iter1.next()
                    head1.lineNumber > head2.lineNumber ->
                        iter2.next()
                    else -> {
                        head1.addHits(head2.hits)
                        iter1.next()
                        iter2.next()
                        head1
                    }
                }
                add(next)
            }
            iter1.forEachRemaining(::add)
            iter2.forEachRemaining(::add)
        }
    }
}
