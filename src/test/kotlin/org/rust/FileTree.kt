/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.RsReferenceElementBase
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.resolve.ResolveResult
import org.rust.lang.core.resolve.checkResolvedFile
import org.rust.openapiext.*
import org.rust.stdext.exhaustive
import java.nio.file.Files
import kotlin.text.Charsets.UTF_8

fun fileTree(builder: FileTreeBuilder.() -> Unit): FileTree =
    FileTree(FileTreeBuilderImpl().apply { builder() }.intoDirectory())

fun fileTreeFromText(@Language("Rust") text: String): FileTree {
    val fileSeparator = """^\s*//- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
    val fileNames = fileSeparator.findAll(text).map { it.groupValues[1] }.toList()
    val fileTexts = fileSeparator.split(text)
        .let {
            check(it.first().isBlank())
            it.drop(1)
        }
        .map { it.trimIndent() }

    check(fileNames.size == fileTexts.size) {
        "Have you placed `//- filename.rs` markers?"
    }

    fun fill(dir: Entry.Directory, path: List<String>, contents: String) {
        val name = path.first()
        if (path.size == 1) {
            dir.children[name] = Entry.File(contents)
        } else {
            val childDir = dir.children.getOrPut(name) { Entry.Directory(mutableMapOf()) } as Entry.Directory
            fill(childDir, path.drop(1), contents)
        }
    }

    return FileTree(Entry.Directory(mutableMapOf()).apply {
        for ((path, contents) in fileNames.map { it.split("/") }.zip(fileTexts)) {
            fill(this, path, contents)
        }
    })
}

interface FileTreeBuilder {
    fun dir(name: String, builder: FileTreeBuilder.() -> Unit)
    fun dir(name: String, tree: FileTree)
    fun file(name: String, code: String)
    fun symlink(name: String, targetPath: String)

    fun rust(name: String, @Language("Rust") code: String) = file(name, code)
    fun toml(name: String, @Language("TOML") code: String) = file(name, code)
}

class FileTree(val rootDirectory: Entry.Directory) {
    fun create(project: Project, directory: VirtualFile): TestProject {
        val filesWithCaret: MutableList<String> = mutableListOf()
        val filesWithSelection: MutableList<String> = mutableListOf()

        fun go(dir: Entry.Directory, root: VirtualFile, parentComponents: List<String> = emptyList()) {
            for ((name, entry) in dir.children) {
                val components = parentComponents + name
                when (entry) {
                    is Entry.File -> {
                        val vFile = root.findChild(name) ?: root.createChildData(root, name)
                        VfsUtil.saveText(vFile, replaceCaretMarker(entry.text))
                        if (hasCaretMarker(entry.text) || "//^" in entry.text || "#^" in entry.text) {
                            filesWithCaret += components.joinToString(separator = "/")
                        }
                        if (hasSelectionMarker(entry.text)) {
                            filesWithSelection += components.joinToString(separator = "/")
                        }
                        Unit
                    }
                    is Entry.Directory -> {
                        go(entry, root.createChildDirectory(root, name), components)
                    }
                    is Entry.Symlink -> {
                        check(root.fileSystem == LocalFileSystem.getInstance()) {
                            "Symlinks are available only in LocalFileSystem"
                        }
                        Files.createSymbolicLink(root.pathAsPath.resolve(name), root.pathAsPath.resolve(entry.targetPath))
                        Unit
                    }
                }.exhaustive
            }
        }

        runWriteAction {
            go(rootDirectory, directory)
            fullyRefreshDirectory(directory)
        }

        return TestProject(project, directory, filesWithCaret, filesWithSelection)
    }

    fun assertEquals(baseDir: VirtualFile) {
        fun go(expected: Entry.Directory, actual: VirtualFile) {
            val actualChildren = actual.children.associateBy { it.name }
            check(expected.children.keys == actualChildren.keys) {
                "Mismatch in directory ${actual.path}\n" +
                    "Expected: ${expected.children.keys}\n" +
                    "Actual  : ${actualChildren.keys}"
            }

            for ((name, entry) in expected.children) {
                val a = actualChildren[name]!!
                when (entry) {
                    is Entry.File -> {
                        check(!a.isDirectory)
                        val actualText = convertLineSeparators(String(a.contentsToByteArray(), UTF_8))
                        Assert.assertEquals(entry.text.trimEnd(), actualText.trimEnd())
                    }
                    is Entry.Directory -> go(entry, a)
                    is Entry.Symlink -> error("Symlink comparison is not supported!")
                }.exhaustive
            }
        }

        saveAllDocuments()
        go(rootDirectory, baseDir)
    }

    fun check(fixture: CodeInsightTestFixture) {
        fun go(dir: Entry.Directory, rootPath: String) {
            for ((name, entry) in dir.children) {
                val path = "$rootPath/$name"
                when (entry) {
                    is Entry.File -> fixture.checkResult(path, entry.text, true)
                    is Entry.Directory -> go(entry, path)
                    is Entry.Symlink -> error("Symlink comparison is not supported!")
                }.exhaustive
            }
        }

        go(rootDirectory, ".")
    }
}

fun FileTree.create(fixture: CodeInsightTestFixture): TestProject =
    create(fixture.project, fixture.findFileInTempDir("."))

fun FileTree.createAndOpenFileWithCaretMarker(fixture: CodeInsightTestFixture): TestProject {
    val testProject = create(fixture)
    fixture.configureFromTempProjectFile(testProject.fileWithCaret)
    return testProject
}

class TestProject(
    private val project: Project,
    val root: VirtualFile,
    private val filesWithCaret: List<String>,
    private val filesWithSelection: List<String>
) {

    val fileWithCaret: String
        get() = when (filesWithCaret.size) {
            1 -> filesWithCaret.single()
            0 -> error("Please, add `/*caret*/` or `<caret>` marker to some file")
            else -> error("More than one file with carets found: $filesWithCaret")
        }

    val fileWithCaretOrSelection: String get() = filesWithCaret.singleOrNull() ?: filesWithSelection.single()

    inline fun <reified T : PsiElement> findElementInFile(path: String): T {
        return doFindElementInFile(path, T::class.java)
    }

    inline fun <reified T : RsReferenceElement> checkReferenceIsResolved(
        path: String,
        shouldNotResolve: Boolean = false,
        toCrate: String? = null,
        toFile: String? = null
    ) {
        val ref = findElementInFile<T>(path)
        val reference = ref.reference ?: error("Failed to get reference for `${ref.text}`")
        val res = reference.resolve()
        if (shouldNotResolve) {
            check(res == null) {
                "Reference ${ref.text} should be unresolved in `$path`"
            }
        } else {
            check(res != null) {
                "Failed to resolve the reference `${ref.text}` in `$path`."
            }
            if (toCrate != null) {
                val pkg = res.containingCargoPackage?.let { "${it.name} ${it.version}" } ?: "[nowhere]"
                check(pkg == toCrate) {
                    "Expected to be resolved to $toCrate but actually resolved to $pkg"
                }
            }
            if (toFile != null) {
                val file = res.containingFile.virtualFile
                val result = checkResolvedFile(file, toFile) { file.fileSystem.findFileByPath(it) }
                check(result !is ResolveResult.Err) {
                    (result as ResolveResult.Err).message
                }
            }
        }
    }

    fun checkResolveInAllFiles() {
        for (path in filesWithCaret) {
            val file = file(path).toPsiFile(project)!!

            val (refElement, data, offset) = findElementWithDataAndOffsetInFile(file, RsReferenceElementBase::class.java, "^")

            if (data == "unresolved") {
                val resolved = refElement.reference?.resolve()
                check(resolved == null) {
                    "$path: $refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
                }
                return
            }

            val resolved = refElement.checkedResolve(offset, errorMessagePrefix = "$path: ")
            val target = findElementInFile(file, RsNamedElement::class.java, "X")

            check(resolved == target) {
                "$path: $refElement `${refElement.text}` should resolve to $target (${target.text}), was $resolved (${resolved.text}) instead"
            }
        }
    }

    fun <T : PsiElement> doFindElementInFile(path: String, psiClass: Class<T>): T {
        val file = file(path).toPsiFile(project)!!
        return findElementInFile(file, psiClass, "^")
    }

    private fun <T : PsiElement> findElementInFile(file: PsiFile, psiClass: Class<T>, marker: String): T {
        val (element, data, _) = findElementWithDataAndOffsetInFile(file, psiClass, marker)
        check(data.isEmpty()) { "Did not expect marker data" }
        return element
    }

    private fun <T : PsiElement> findElementWithDataAndOffsetInFile(
        file: PsiFile,
        psiClass: Class<T>,
        marker: String
    ): Triple<T, String, Int> {
        val elementsWithDataAndOffset = findElementsWithDataAndOffsetInFile(file, psiClass, marker)
        check(elementsWithDataAndOffset.isNotEmpty()) { "No `$marker` marker:\n${file.text}" }
        check(elementsWithDataAndOffset.size <= 1) { "More than one `$marker` marker:\n${file.text}" }
        return elementsWithDataAndOffset.first()
    }

    private fun <T : PsiElement> findElementsWithDataAndOffsetInFile(
        file: PsiFile,
        psiClass: Class<T>,
        marker: String
    ): List<Triple<T, String, Int>> {
        return findElementsWithDataAndOffsetInEditor(
            file,
            file.document!!,
            followMacroExpansions = true,
            psiClass,
            marker
        )
    }

    fun psiFile(path: String): PsiFileSystemItem {
        val vFile = file(path)
        val psiManager = PsiManager.getInstance(project)
        return if (vFile.isDirectory) psiManager.findDirectory(vFile)!! else psiManager.findFile(vFile)!!
    }

    fun file(path: String): VirtualFile {
        return root.findFileByRelativePath(path) ?: error("Can't find `$path`")
    }
}


private class FileTreeBuilderImpl(val directory: MutableMap<String, Entry> = mutableMapOf()) : FileTreeBuilder {
    override fun dir(name: String, builder: FileTreeBuilder.() -> Unit) {
        check('/' !in name) { "Bad directory name `$name`" }
        directory[name] = FileTreeBuilderImpl().apply { builder() }.intoDirectory()
    }

    override fun dir(name: String, tree: FileTree) {
        check('/' !in name) { "Bad directory name `$name`" }
        directory[name] = tree.rootDirectory
    }

    override fun file(name: String, code: String) {
        check('/' !in name) { "Bad file name `$name`" }
        directory[name] = Entry.File(code.trimIndent())
    }

    override fun symlink(name: String, targetPath: String) {
        directory[name] = Entry.Symlink(targetPath)
    }

    fun intoDirectory() = Entry.Directory(directory)
}

sealed class Entry {
    class File(val text: String) : Entry()
    class Directory(val children: MutableMap<String, Entry>) : Entry()
    class Symlink(val targetPath: String) : Entry()
}

fun replaceCaretMarker(text: String): String = text.replace("/*caret*/", "<caret>")
fun hasCaretMarker(text: String): Boolean = text.contains("/*caret*/") || text.contains("<caret>")
fun hasSelectionMarker(text: String): Boolean = text.contains("<selection>") && text.contains("</selection>")
