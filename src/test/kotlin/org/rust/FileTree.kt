/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.containingCargoPackage
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.utils.fullyRefreshDirectory
import kotlin.text.Charsets.UTF_8

fun fileTree(builder: FileTreeBuilder.() -> Unit): FileTree {
    return FileTree(FileTreeBuilderImpl().apply { builder() }.intoDirectory())
}

interface FileTreeBuilder {
    fun dir(name: String, builder: FileTreeBuilder.() -> Unit)
    fun file(name: String, code: String)

    fun rust(name: String, @Language("Rust") code: String) = file(name, code)
    fun toml(name: String, @Language("TOML") code: String) = file(name, code)
}

class FileTree(private val rootDirectory: Entry.Directory) {
    fun create(project: Project, directory: VirtualFile): TestProject {
        fun go(dir: Entry.Directory, root: VirtualFile) {
            for ((name, entry) in dir.children) {
                when (entry) {
                    is Entry.File -> {
                        val vFile = root.createChildData(root, name)
                        VfsUtil.saveText(vFile, entry.text)
                    }
                    is Entry.Directory -> {
                        go(entry, root.createChildDirectory(root, name))
                    }
                }
            }
        }

        runWriteAction {
            go(rootDirectory, directory)
            fullyRefreshDirectory(directory)
        }

        return TestProject(project, directory)
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
                        val actualText = String(a.contentsToByteArray(), UTF_8)
                        check(entry.text == actualText)
                    }
                    is Entry.Directory -> go(entry, a)
                }
            }
        }

        go(rootDirectory, baseDir)
    }
}

class TestProject(
    private val project: Project,
    val root: VirtualFile
) {

    inline fun <reified T : PsiElement> findElementInFile(path: String): T {
        val element = doFindElementInFile(path)
        return element.parentOfType<T>()
            ?: error("No parent of type ${T::class.java} for ${element.text}")
    }

    inline fun <reified T : RsReferenceElement> checkReferenceIsResolved(
        path: String,
        shouldNotResolve: Boolean = false,
        toCrate: String? = null
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
            if (toCrate != null && res != null) {
                val pkg = res.containingCargoPackage?.let { "${it.name} ${it.version}" } ?: "[nowhere]"
                check(pkg == toCrate) {
                    "Expected to be resolved to $toCrate but actually resolved to $pkg"
                }
            }
        }
    }

    fun doFindElementInFile(path: String): PsiElement {
        val vFile = root.findFileByRelativePath(path)
            ?: error("No `$path` file in test project")
        val file = PsiManager.getInstance(project).findFile(vFile)!!
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

    override fun file(name: String, code: String) {
        check('/' !in name && '.' in name) { "Bad file name `$name`" }
        directory[name] = Entry.File(code)
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
