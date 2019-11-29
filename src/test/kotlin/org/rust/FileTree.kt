/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil.convertLineSeparators
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.intellij.lang.annotations.Language
import org.junit.Assert
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.resolve.ResolveResult
import org.rust.lang.core.resolve.checkResolvedFile
import org.rust.openapiext.fullyRefreshDirectory
import org.rust.openapiext.saveAllDocuments
import org.rust.openapiext.toPsiFile
import kotlin.text.Charsets.UTF_8

fun fileTree(builder: FileTreeBuilder.() -> Unit): FileTree =
    FileTree(FileTreeBuilderImpl().apply { builder() }.intoDirectory())

fun fileTreeFromText(@Language("Rust") text: String): FileTree {
    val fileSeparator = """^\s*//- (\S+)\s*$""".toRegex(RegexOption.MULTILINE)
    val fileNames = fileSeparator.findAll(text).map { it.groupValues[1] }.toList()
    val fileTexts = fileSeparator.split(text).dropWhile(String::isBlank).map { it.trimIndent() }

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

    fun rust(name: String, @Language("Rust") code: String) = file(name, code)
    fun toml(name: String, @Language("TOML") code: String) = file(name, code)
}

class FileTree(val rootDirectory: Entry.Directory) {
    fun create(project: Project, directory: VirtualFile): TestProject {
        val filesWithCaret: MutableList<String> = mutableListOf()

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
                    }
                    is Entry.Directory -> {
                        go(entry, root.createChildDirectory(root, name), components)
                    }
                }
            }
        }

        runWriteAction {
            go(rootDirectory, directory)
            fullyRefreshDirectory(directory)
        }

        return TestProject(project, directory, filesWithCaret)
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
                }
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
                }
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
    private val filesWithCaret: List<String>
) {

    val fileWithCaret: String get() = filesWithCaret.singleOrNull()!!

    inline fun <reified T : PsiElement> findElementInFile(path: String): T {
        val element = doFindElementInFile(path)
        return element.ancestorStrict()
            ?: error("No parent of type ${T::class.java} for ${element.text}")
    }

    inline fun <reified T : RsReferenceElement> checkReferenceIsResolved(
        path: String,
        shouldNotResolve: Boolean = false,
        toCrate: String? = null,
        toFile: String? = null
    ) {
        val ref = findElementInFile<T>(path)
        val res = ref.reference.resolve()
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

    fun doFindElementInFile(path: String): PsiElement {
        val vFile = root.findFileByRelativePath(path)
            ?: error("No `$path` file in test project")
        val file = vFile.toPsiFile(project)!!
        return findElementInFile(file, "^")
    }

    fun psiFile(path: String): PsiFileSystemItem {
        val vFile = root.findFileByRelativePath(path)
            ?: error("Can't find `$path`")
        val psiManager = PsiManager.getInstance(project)
        return if (vFile.isDirectory) psiManager.findDirectory(vFile)!! else psiManager.findFile(vFile)!!
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
        check('/' !in name && '.' in name) { "Bad file name `$name`" }
        directory[name] = Entry.File(code.trimIndent())
    }

    fun intoDirectory() = Entry.Directory(directory)
}

sealed class Entry {
    class File(val text: String) : Entry()
    class Directory(val children: MutableMap<String, Entry>) : Entry()
}

private fun findElementInFile(file: PsiFile, marker: String): PsiElement {
    val markerOffset = file.text.indexOf(marker)
    check(markerOffset != -1) { "No `$marker` in \n${file.text}" }

    val doc = PsiDocumentManager.getInstance(file.project).getDocument(file)!!
    val markerLine = doc.getLineNumber(markerOffset)
    val makerColumn = markerOffset - doc.getLineStartOffset(markerLine)
    val elementOffset = doc.getLineStartOffset(markerLine - 1) + makerColumn

    return file.findElementAt(elementOffset) ?:
        error { "No element found, offset = $elementOffset" }
}

fun replaceCaretMarker(text: String): String = text.replace("/*caret*/", "<caret>")
fun hasCaretMarker(text: String): Boolean = text.contains("/*caret*/") || text.contains("<caret>")
