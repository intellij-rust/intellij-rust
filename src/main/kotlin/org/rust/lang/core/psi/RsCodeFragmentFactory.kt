package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import org.rust.cargo.project.workspace.CargoWorkspace

class RsCodeFragmentFactory(private val project: Project) {
    private val psiFactory = RsPsiFactory(project)

    fun createCrateRelativePath(path: String, target: CargoWorkspace.Target): RsPathExpr? {
        check(path.startsWith("::"))
        val vFile = target.crateRoot ?: return null
        val crateRoot = PsiManager.getInstance(project).findFile(vFile) as? RsFile ?: return null
        val expr = psiFactory.tryCreatePathExpr(path) ?: return null
        expr.setContext(crateRoot)
        return expr
    }
}

val CODE_FRAGMENT_FILE = Key.create<RsFile>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

private fun RsPathExpr.setContext(file: RsFile) {
    putUserData(CODE_FRAGMENT_FILE, file)
}

