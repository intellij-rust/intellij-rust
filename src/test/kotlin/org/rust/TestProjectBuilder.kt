package org.rust

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import org.intellij.lang.annotations.Language
import org.rust.lang.core.psi.ext.RsReferenceElement
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.utils.fullyRefreshDirectory

class TestProjectBuilder(
    private val project: Project,
    private val root: VirtualFile = project.baseDir
) {

    fun rust(path: String, @Language("Rust") code: String) = file(path, code, "rs")

    fun toml(path: String, @Language("TOML") code: String) = file(path, code, "toml")

    fun dir(path: String, builder: TestProjectBuilder.() -> Unit) {
        val vDir = VfsUtil.createDirectoryIfMissing(root, path)
        TestProjectBuilder(project, vDir).builder()
    }

    private fun file(path: String, @Language("Rust") code: String, ext: String) {
        check(path.endsWith(".$ext"))
        val dir = PathUtil.getParentPath(path)
        val vDir = VfsUtil.createDirectoryIfMissing(root, dir)
        val vFile = vDir.createChildData(this, PathUtil.getFileName(path))
        VfsUtil.saveText(vFile, code.trimIndent())
    }

    fun build(builder: TestProjectBuilder.() -> Unit): TestProject {
        runWriteAction {
            builder()
            fullyRefreshDirectory(root)
        }
        return TestProject(project, root)
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
        shouldNotResolve: Boolean = false
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
        }
    }

    fun doFindElementInFile(path: String): PsiElement {
        val vFile = root.findFileByRelativePath(path)
            ?: error("No `$path` file in test project")
        val file = PsiManager.getInstance(project).findFile(vFile)!!
        return findElementInFile(file, "^")
    }
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
