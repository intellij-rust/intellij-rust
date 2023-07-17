/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.toml.search

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.model.cargoProjects
import org.rust.lang.core.psi.ext.startOffset
import org.rust.openapiext.document
import org.rust.openapiext.toPsiFile

class CargoTomlFindUsagesTest : RsWithToolchainTestBase() {

    fun test() = doTest {
        toml("Cargo.toml", """
            [package]
            name = "hello"
            version = "0.1.0"
            authors = []

            [features]
            foo = ["dep_pkg/feature_bar"] # - Cargo feature dependency

            [dependencies]
            dep_pkg = { path = "./dep_pkg", features = ["feature_bar"] } # - Package dependency
        """)

        dir("src") {
            rust("main.rs", """
                fn main() {}
            """)
        }

        dir("dep_pkg") {
            toml("Cargo.toml", """
                [package]
                name = "dep_pkg"
                version = "0.1.0"
                authors = []

                [features]
                feature_foo = ["feature_bar"] # - Cargo feature dependency
                feature_bar = []
                #^
            """)

            dir("src") {
                rust("lib.rs", """
                    #[cfg(feature = "feature_bar")] // - Cfg attribute
                    #[cfg_attr(feature = "feature_bar", allow(all))] // - Cfg attribute
                    fn foobar() {}
                """)
            }
        }
    }

    private fun doTest(fileTree: FileTreeBuilder.() -> Unit) {
        val testProject = buildProject(fileTree)

        assertNotNull(project.cargoProjects.allProjects.single().workspace)
        val allFiles = mutableListOf<VirtualFile>()
        VfsUtilCore.iterateChildrenRecursively(testProject.root, null) {
            if (!it.isDirectory) {
                allFiles += it
            }
            true
        }
        val vFile = testProject.file(testProject.fileWithCaret)
        val psiFile = vFile.toPsiFile(project)!!

        myFixture.openFileInEditor(vFile)

        val markerOffset = psiFile.text.indexOf("^")
        check(markerOffset != -1) { "No `^` in \n${psiFile.text}" }

        val doc = psiFile.document!!
        val markerLine = doc.getLineNumber(markerOffset)
        val makerColumn = markerOffset - doc.getLineStartOffset(markerLine)
        val elementOffset = doc.getLineStartOffset(markerLine - 1) + makerColumn

        val source = TargetElementUtil.getInstance().findTargetElement(
            myFixture.editor,
            TargetElementUtil.ELEMENT_NAME_ACCEPTED,
            elementOffset
        ) as? PsiNamedElement ?: error("Element not found")

        val markersActual = myFixture.findUsages(source)
            .mapNotNull { it.element }
            .groupBy { it.containingFile.virtualFile!! }
            .mapValues { (_, v) ->
                v.map { Pair(it.line ?: -1, CargoTomlUsageTypeProvider().getUsageType(it).toString()) }
                    .sortedBy { it.first }
            }
            .toMutableMap()

        val expectedMarkers = allFiles.mapNotNull { file ->
            markersFrom(file).takeIf { it.isNotEmpty() }?.let { file to it }
        }.toMap()

        for ((file, expected) in expectedMarkers) {
            val actual = markersActual.remove(file) ?: error("Expected usages in $file, but found 0 usages")
            assertEquals(expected.joinToString("\n"), actual.joinToString("\n"))
        }

        if (markersActual.isNotEmpty()) {
            error("Extra usages in $markersActual")
        }
    }

    private fun markersFrom(file: VirtualFile): List<Pair<Int, String>> {
        val text = VfsUtil.loadText(file)
        val commentPrefix = LanguageCommenters.INSTANCE.forLanguage(file.toPsiFile(project)!!.language).lineCommentPrefix ?: "//"
        val marker = "$commentPrefix - "
        return text.split('\n')
            .withIndex()
            .filter { it.value.contains(marker) }
            .map { Pair(it.index, it.value.substring(it.value.indexOf(marker) + marker.length).trim()) }
    }

    private val PsiElement.line: Int? get() = containingFile.viewProvider.document?.getLineNumber(startOffset)
}
