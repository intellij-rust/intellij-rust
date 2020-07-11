/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.impl.source.tree.LazyParseableElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.util.LocalTimeCounter
import junit.framework.TestCase
import org.rust.RsTestBase
import org.rust.lang.RsFileType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.descendantsOfType
import org.rustPerformanceTests.fullyRefreshDirectoryInUnitTests

/**
 * Base class for tests to check that block elements are not parsed if they don't contain stub elements
 */
abstract class RsLazyBlockStubCreationTestBase : RsTestBase() {

    protected fun checkRustFiles(directory: VirtualFile, ignored: Collection<String>) {
        val files = collectRustFiles(directory, ignored)
            .mapNotNull { it.toInMemoryPsiFile() as? RsFile }

        var numBlocks = 0
        var numParsedBlocks = 0

        for (psi in files) {
            parseFile(psi)
            val blocks = SyntaxTraverser.psiTraverser(psi).expand { it !is RsBlock }.filterIsInstance<RsBlock>()

            blocks.forEach {
                check(it.parent !is RsFunction || !it.isParsed) {
                    "Expected NOT parsed block: `${it.text}`"
                }
            }

            val stubBuilder = RsFileStub.Type.builder
            val stub1 = stubBuilder.buildStubTree(psi)

            for (block in blocks) {
                if (block.parent !is RsFunction) continue
                numBlocks++
                val skipChildProcessing = stubBuilder.skipChildProcessingWhenBuildingStubs(block.node.treeParent, block.node)
                val blockIsParsed = block.isParsed

                check(skipChildProcessing == !blockIsParsed) {
                    "Sanity check failed; skipChildProcessing = $skipChildProcessing, blockIsParsed = $blockIsParsed"
                }
                parseBlock(block)

                val blockIsStubbed = block.elementType.shouldCreateStub(block.node)

                val stubbedElements = block.descendantsOfType<StubBasedPsiElement<*>>()
                    .filter { it.elementType.shouldCreateStub(it.node) }
                val containsStubbedElements = stubbedElements.isNotEmpty()

                // Macros can contain any tokens, so our heuristic can give false-positives; allow them
                val parsingAllowed = containsStubbedElements ||
                    block.descendantsOfType<RsMacroCall>().isNotEmpty() ||
                    block.descendantsOfType<RsMacro>().isNotEmpty() ||
                    block.descendantsOfType<RsMacro2>().isNotEmpty()

                if (blockIsParsed) {
                    numParsedBlocks++
                    check(parsingAllowed) { "Expected NOT parsed block after stub tree building: `${block.text}`" }
                    if (containsStubbedElements) {
                        check(blockIsStubbed) {
                            "Expected `block.elementType.shouldCreateStub` returning `true` " +
                                "(b/c the block contains stubbed elements), got `false` for block: `${block.text}`"
                        }
                    }
                } else {
                    check(!containsStubbedElements) {
                        "Expected PARSED block after stub tree building: `${block.text}`\n" +
                            "Because it contains elements that should be stubbed: " +
                            stubbedElements.joinToString { "`${it.text}`" }
                    }
                    check(!blockIsStubbed) {
                        "Expected `block.elementType.shouldCreateStub` returning `false` " +
                            "(b/c the block is not parsed), got `true` for block: `${block.text}`"
                    }
                }
            }

            // Check stubs are the same after full reparse
            val stub2 = stubBuilder.buildStubTree(psi)
            TestCase.assertEquals(DebugUtil.stubTreeToString(stub1), DebugUtil.stubTreeToString(stub2))
        }

        println("Blocks: $numBlocks, parsed: $numParsedBlocks (${(numParsedBlocks * 1000 / numBlocks)/10.0}%)")
    }

    private fun parseFile(psi: RsFile) { // profiler hint
        TreeUtil.ensureParsed(psi.node)
    }

    private fun parseBlock(psi: RsBlock) { // profiler hint
        TreeUtil.ensureParsed(psi.node)
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

    private val RsBlock.isParsed get() = (node as LazyParseableElement).isParsed
}
