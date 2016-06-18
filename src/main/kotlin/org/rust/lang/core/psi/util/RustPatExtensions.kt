package org.rust.lang.core.psi.util

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.*


/**
 * Extracts all the names bound by this particular [RustPatElement]
 */
val RustPatElement.boundElements: List<RustNamedElement>
    get() {
        val result = arrayListOf<RustNamedElement>()

        accept(object : RustElementVisitor() {
            override fun visitElement(element: PsiElement) {
                throw UnsupportedOperationException("Visiting un-handled type of the `pat`! Please handle me properly [ ${element.elementType} ]")
            }

            fun visit(o: RustPatElement?) = o?.accept(this)
            fun visit(o: List<RustPatElement>) = o.forEach { visit(it) }

            override fun visitPatBinding(o: RustPatBindingElement) {
                result.add(o)
            }

            override fun visitPatEnum(o: RustPatEnumElement) {
                visit(o.patList)
            }

            override fun visitPatField(o: RustPatFieldElement) {
                visit(o.pat)
            }

            override fun visitPatIdent(o: RustPatIdentElement) {
                result.add(o.patBinding)
                visit(o.pat)
            }

            override fun visitPatRef(o: RustPatRefElement) {
                visit(o.pat)
            }

            override fun visitPatStruct(o: RustPatStructElement) {
                o.patFieldList.forEach { field ->
                    field.patBinding?.let { result.add(it) } ?: visit(field.pat)
                }
            }

            override fun visitPatTup(o: RustPatTupElement) {
                visit(o.patList)
            }

            override fun visitPatUniq(o: RustPatUniqElement) {
                visit(o.pat)
            }

            override fun visitPatVec(o: RustPatVecElement) {
                visit(o.patList)
            }

            override fun visitPatQualPath(o: RustPatQualPathElement) {
                // NOP
            }

            override fun visitPatRange(o: RustPatRangeElement) {
                // NOP
            }

            override fun visitPatWild(o: RustPatWildElement) {
                // NOP
            }

            override fun visitPatMacro(o: RustPatMacroElement) {
                // TODO(xxx): Fix me
            }
        })

        return result
    }


/**
 * Seeks for an index of the given [pat] inside the embracing [RustPatTupElement]
 */
fun RustPatTupElement.indexOf(pat: RustPatElement): Int {
    check(pat.parent === this)

    return patList.indexOf(pat)
}
