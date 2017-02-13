package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.DebugUtil
import org.junit.experimental.categories.Category
import org.rust.Performance
import org.rust.lang.RsFileType
import org.rust.lang.RsTestBase
import java.util.*
import kotlin.system.measureTimeMillis

@Category(Performance::class)
class RsParserPerformanceTest : RsTestBase() {
    override val dataPath: String = ""

    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun testHighlightingPerformance() {
        val file = rustSrcDir().findFileByRelativePath("libsyntax/parse/parser.rs")!!
        val fileContents = String(file.contentsToByteArray())
        myFixture.configureByText("parser.rs", fileContents)
        val elapsed = myFixture.checkHighlighting()
        reportTeamCityMetric(name, elapsed)
    }

    fun testParsingCompilerSources() {
        val sources = rustSrcDir()
        parseRustFiles(
            sources,
            ignored = setOf("test", "doc", "etc", "grammar"),
            expectedNumberOfFiles = 800,
            checkForErrors = true
        )
    }

    fun testParsingCompilerTests() {
        val testDir = rustSrcDir().findFileByRelativePath("test")!!
        parseRustFiles(
            testDir,
            ignored = emptyList(),
            expectedNumberOfFiles = 4000,
            checkForErrors = false
        )
    }

    private data class FileStats(
        val path: String,
        val time: Long,
        val fileLength: Int
    )

    private fun parseRustFiles(directory: VirtualFile,
                               ignored: Collection<String>,
                               expectedNumberOfFiles: Int,
                               checkForErrors: Boolean) {
        val processed = ArrayList<FileStats>()
        val totalTime = measureTimeMillis {
            VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Void>() {
                override fun visitFileEx(file: VirtualFile): Result {
                    if (file.isDirectory && file.name in ignored) return SKIP_CHILDREN

                    // BACKCOMPAT: Rust 1.16.0
                    // There is a syntax error in this file
                    // https://github.com/rust-lang/rust/pull/37278
                    if (file.path.endsWith("dataflow/graphviz.rs")) return CONTINUE

                    if (file.fileType != RsFileType) return CONTINUE
                    val fileContent = String(file.contentsToByteArray())

                    val time = measureTimeMillis {
                        val psi = PsiFileFactory.getInstance(project).createFileFromText(file.name, file.fileType, fileContent)
                        val psiString = DebugUtil.psiToString(psi, /* skipWhitespace = */ true)

                        if (checkForErrors) {
                            check("PsiErrorElement" !in psiString) {
                                "Failed to parse ${file.path}\n\n$fileContent\n\n$psiString\n\n${file.path}"
                            }
                        }
                    }

                    val relPath = FileUtil.getRelativePath(directory.path, file.path, '/')!!
                    processed += FileStats(relPath, time, fileContent.length)
                    return CONTINUE
                }
            })
        }
        check(processed.size > expectedNumberOfFiles)

        reportTeamCityMetric("$name totalTime", totalTime)

        println("\n$name " +
            "\nTotal: ${totalTime}ms" +
            "\nFiles: ${processed.size}")
        val slowest = processed.sortedByDescending { it.time }.take(5)
        println("\nSlowest files")
        for ((path, time, fileLength) in slowest) {
            println("${"%3d".format(time)}ms ${"%3d".format(fileLength / 1024)}kb: $path")
        }
        println()
    }

    private fun rustSrcDir(): VirtualFile = projectDescriptor.stdlib!!
}
