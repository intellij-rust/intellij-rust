package org.rust.lang.core.parser

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.DebugUtil
import org.junit.experimental.categories.Category
import org.rust.Performance
import org.rust.lang.RustFileType
import org.rust.lang.RustTestCaseBase
import org.rust.lang.core.psi.RustReferenceElement
import org.rust.lang.core.psi.visitors.RustRecursiveElementVisitor
import java.util.*
import kotlin.system.measureTimeMillis

@Category(Performance::class)
class RustParserPerformanceTest : RustTestCaseBase() {
    override val dataPath: String = ""

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
                    if (file.fileType != RustFileType) return CONTINUE
                    val fileContent = String(file.contentsToByteArray())

                    val time = measureTimeMillis {
                        val psi = PsiFileFactory.getInstance(project).createFileFromText(file.name, file.fileType, fileContent)
                        try {
                            psi.accept(object : RustRecursiveElementVisitor() {
                                override fun visitElement(element: PsiElement) {
                                    super.visitElement(element)
                                    if (element is RustReferenceElement) {
                                        element.reference.resolve()
                                    }
                                }
                            })
                        } catch(e: Throwable) {
                            println(file.path)
                            throw e
                        }
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

        println("\n$name " +
            "\nTotal: ${totalTime}ms" +
            "\nFiles: ${processed.size}")
        val slowest = processed.sortedByDescending { it.time }.take(5)
        println("\nSlowest files")
        for (stats in slowest) {
            println("${"%3d".format(stats.time)}ms ${"%3d".format(stats.fileLength / 1024)}kb: ${stats.path}")
        }
        println()
    }

    private fun rustSrcDir(): VirtualFile =
        JarFileSystem.getInstance().getJarRootForLocalFile(rustSourcesArchive())
            ?.children?.singleOrNull()
            ?.findChild("src")!!
}

