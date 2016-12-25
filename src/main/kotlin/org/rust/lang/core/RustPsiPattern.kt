import com.intellij.patterns.PatternCondition
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.psi.RustTokenElementTypes

/**
 * Rust PSI tree patterns.
 */
class RustPsiPattern {
    class StatementBeginning : PatternCondition<PsiElement>("on statement beginning") {
        override fun accepts(t: PsiElement, context: ProcessingContext?): Boolean {
            if (PsiTreeUtil.prevLeaf(t) == null) return true
            val prev = t.prevVisibleOrNewLine ?: return false
            if (prev is PsiWhiteSpace) return true
            return prev.node.elementType in STATEMENT_BOUNDARIES
        }
    }

    private companion object {
        val STATEMENT_BOUNDARIES = listOf(RustTokenElementTypes.SEMICOLON, RustTokenElementTypes.LBRACE, RustTokenElementTypes.RBRACE)
    }
}

val PsiElement.prevVisibleOrNewLine: PsiElement?
    get() = leftLeaves
        .filterNot { it is PsiComment || it is PsiErrorElement }
        .filter { it !is PsiWhiteSpace || it.textContains('\n') }
        .firstOrNull()

val PsiElement.leftLeaves: Sequence<PsiElement> get() = generateSequence(this, PsiTreeUtil::prevLeaf).drop(1)

val PsiElement.rightSiblings: Sequence<PsiElement> get() = generateSequence(this.nextSibling) { it.nextSibling }
