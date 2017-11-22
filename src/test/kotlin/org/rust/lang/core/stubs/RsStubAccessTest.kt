/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.openapi.util.Iconable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.StubElement
import com.intellij.testFramework.LoggedErrorProcessor
import org.apache.log4j.Logger
import org.rust.lang.RsTestBase
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.RsNamedElement
import java.util.*

class RsStubAccessTest : RsTestBase() {
    override val dataPath = "org/rust/lang/core/stubs/fixtures"

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject(".", "src")
    }

    fun `test presentation does not need ast`() {
        processStubsWithoutAstAccess<RsNamedElement> { element ->
            element.getIcon(0)
            element.getIcon(Iconable.ICON_FLAG_VISIBILITY)
            element.name
            element.presentation?.let {
                it.locationString
                it.presentableText
                it.getIcon(false)
            }
        }
    }

    fun `test getting reference does not need ast`() {
        processStubsWithoutAstAccess<RsElement> { it.reference }
    }

    fun `test parent works correctly for stubbed elements`() {
        val parentsByStub: MutableMap<PsiElement, PsiElement> = HashMap()
        try {
            LoggedErrorProcessor.setNewInstance(object : LoggedErrorProcessor() {
                override fun processError(message: String?, t: Throwable?, details: Array<out String>?, logger: Logger) {
                    logger.info(message, t)
                    throw AssertionError(message)
                }
            })
            processStubsWithoutAstAccess<RsElement> {
                val parent = try {
                    it.parent
                } catch (e: AssertionError) {
                    null
                }
                if (parent != null) {
                    parentsByStub += it to it.parent
                }
            }
        } finally {
            LoggedErrorProcessor.restoreDefaultProcessor()
        }

        checkAstNotLoaded(VirtualFileFilter.NONE)

        for ((element, stubParent) in parentsByStub) {
            element.node // force AST loading
            check(element.parent == stubParent) {
                "parentByStub returned wrong result for $element\n${element.text}"
            }
        }
    }

    private inline fun <reified T : PsiElement> processStubsWithoutAstAccess(block: (T) -> Unit) {
        checkAstNotLoaded(VirtualFileFilter.ALL)

        val work = ArrayDeque<StubElement<*>>()

        VfsUtilCore.visitChildrenRecursively(myFixture.findFileInTempDir("src"), object : VirtualFileVisitor<Void>() {
            override fun visitFileEx(file: VirtualFile): Result {
                if (!file.isDirectory) {
                    work.push((psiManager.findFile(file) as PsiFileImpl).stub!!)
                }

                return CONTINUE
            }
        })

        var processed = 0
        var visited = 0
        while (work.isNotEmpty()) {
            val stub = work.pop()
            val psi = stub.psi
            visited += 1
            if (psi is T) {
                block(psi)
                processed += 1
            }
            work += stub.childrenStubs
        }

        check(visited > 10)
        check(processed > 0)
    }
}

