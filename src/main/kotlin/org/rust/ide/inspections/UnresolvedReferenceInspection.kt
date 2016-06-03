package org.rust.ide.inspections

import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import org.rust.lang.core.psi.RustEnumItemElement
import org.rust.lang.core.psi.RustMod
import org.rust.lang.core.psi.RustPathElement
import org.rust.lang.core.psi.RustElementVisitor

class RustUnresolvedReferenceInspection : RustLocalInspectionTool() {
    override fun getDisplayName(): String = "Unresolved reference"

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : RustElementVisitor() {
            override fun visitPath(o: RustPathElement) {
                if (o.isPrimitive) return
                val resolved = o.reference.resolve()
                if (resolved != null) return
                val parent = o.path
                val parentRes = parent?.reference?.resolve()
                if (parent == null || parentRes is RustMod || parentRes is RustEnumItemElement) {
                    holder.registerProblem(o.navigationElement, "Unresolved reference")
                }
            }
        }
    }
}

private val RustPathElement.isPrimitive: Boolean get() = path == null && name in primitives

private val primitives = setOf(
    "i8", "i16", "i32", "i64", "isize",
    "u8", "u16", "u32", "u64", "usize",
    "char", "str",
    "f32", "f64",
    "bool"
)
