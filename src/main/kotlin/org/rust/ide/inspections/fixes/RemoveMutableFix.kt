package org.rust.ide.annotator.fixes

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import org.rust.lang.core.psi.RsPatBinding

class RemoveMutableFix(val patBinding: RsPatBinding) : LocalQuickFix {
    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        updateMutable(project, patBinding, false)
    }

    override fun getName(): String  = "Remove mutable"
    override fun getFamilyName(): String  = name

}
