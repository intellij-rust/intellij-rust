/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rustPerformanceTests

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.ILazyParseableElementTypeBase
import com.intellij.util.LocalTimeCounter
import org.rust.RsTestBase
import org.rust.WithStdlibRustProjectDescriptor
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.RsBlock
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.doc.psi.RsDocComment
import org.rust.stdext.repeatBenchmark

class RsParsingPerformanceTest : RsTestBase() {
    override fun isPerformanceTest(): Boolean = false
    override fun getProjectDescriptor() = WithStdlibRustProjectDescriptor

    fun `test stdlib source`() {
        val sources = rustSrcDir()
        checkRustFiles(
            sources,
            ignored = setOf("tests", "test", "doc", "etc", "grammar")
        )
    }

    private fun checkRustFiles(directory: VirtualFile, ignored: Collection<String>) {
        val files = collectRustFiles(directory, ignored)

        repeatBenchmark { timings ->
            for (file in files) {
                val psi = file.toInMemoryPsiFile()

                timings.measureSum("root") {
                    TreeUtil.ensureParsed(psi.node)
                }

                val lazyNodes = SyntaxTraverser.psiTraverser(psi)
                    .expand { it.elementType !is ILazyParseableElementTypeBase }
                    .filter { it.elementType is ILazyParseableElementTypeBase }
                    .toList()

                timings.measureSum("lazy docs") {
                    for (node in lazyNodes) {
                        if (node is RsDocComment) {
                            TreeUtil.ensureParsed(node.node)
                        }
                    }
                }

                timings.measureSum("lazy code blocks") {
                    for (node in lazyNodes) {
                        if (node is RsBlock) {
                            TreeUtil.ensureParsed(node.node)
                        }
                    }
                }

                timings.measureSum("lazy other") {
                    for (node in lazyNodes) {
                        TreeUtil.ensureParsed(node.node)
                    }
                }
            }
        }
    }

    private fun collectRustFiles(directory: VirtualFile, ignored: Collection<String>): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        fullyRefreshDirectoryInUnitTests(directory)
        VfsUtilCore.visitChildrenRecursively(directory, object : VirtualFileVisitor<Void>() {
            override fun visitFileEx(file: VirtualFile): Result {
                if (file.isDirectory && file.name in ignored) return SKIP_CHILDREN
                if (file.fileType != RsFileType) return CONTINUE

                files += file

                return CONTINUE
            }
        })
        return files
    }

    private fun VirtualFile.toInMemoryPsiFile(): PsiFile {
        return PsiFileFactory.getInstance(project).createFileFromText(
            name,
            fileType,
            String(contentsToByteArray()),
            LocalTimeCounter.currentTime(),
            true,
            false
        )
    }

    private fun rustSrcDir(): VirtualFile = projectDescriptor.stdlib!!
}
