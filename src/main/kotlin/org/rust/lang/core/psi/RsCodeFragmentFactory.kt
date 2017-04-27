package org.rust.lang.core.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import org.rust.cargo.project.workspace.CargoWorkspace
import org.rust.lang.core.psi.ext.RsCompositeElement
import org.rust.lang.core.psi.ext.setContext

class RsCodeFragmentFactory(private val project: Project) {
    private val psiFactory = RsPsiFactory(project)

    fun createCrateRelativePath(pathText: String, target: CargoWorkspace.Target): RsPath? {
        check(pathText.startsWith("::"))
        val vFile = target.crateRoot ?: return null
        val crateRoot = PsiManager.getInstance(project).findFile(vFile) as? RsFile ?: return null
        return psiFactory.tryCreatePath(pathText)?.apply { setContext(crateRoot) }
    }

    fun createLocalVariable(name: String, context: RsCompositeElement): RsPath? =
        psiFactory.tryCreatePath(name)?.apply { setContext(context) }
}


