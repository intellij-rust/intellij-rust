package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiManager
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.ext.RsCompositeElement

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

    fun createLocalVariable(name: String, context: RsCompositeElement): RsPathExpr? {
        val expr = psiFactory.tryCreatePathExpr(name) ?: return null
        expr.setContext(context)
        return expr
    }
}

// TODO: should probably use `.getContext` instead of our own thing.
val RS_CODE_FRAGMENT_CONTEXT = Key.create<RsCompositeElement>("org.rust.lang.core.psi.CODE_FRAGMENT_FILE")

private fun RsPathExpr.setContext(ctx: RsCompositeElement) {
    putUserData(RS_CODE_FRAGMENT_CONTEXT, ctx)
}

