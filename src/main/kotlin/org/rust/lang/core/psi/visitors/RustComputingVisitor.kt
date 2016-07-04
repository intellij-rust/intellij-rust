package org.rust.lang.core.psi.visitors

import com.intellij.psi.PsiElement
import org.rust.lang.core.psi.RustElementVisitor

/**
 * A value returning visitor designed to compute a function on a PsiElement
 *
 * Note that because [RustElementVisitor] is not designed to return a value,
 * [RustComputingVisitor] must be used with care. To properly initialize result,
 * it is advised that each `visitFoo` method is a call to [set] or a delegation to
 * another visiting method:
 *
 * ```
 * visitFoo(o: Foo) = set {
 *     ...
 * }
 *
 * visitFoo(o: Foo) = visitBar(o)
 * ```
 *
 */
abstract class RustComputingVisitor<R: Any>: RustElementVisitor() {
    private var result: R? = null

    fun compute(element: PsiElement): R {
        element.accept(this)
        return checkNotNull(result) {
            "Element $element was unhandled" +
                "\n${element.containingFile.virtualFile.path}" +
                "\n${element.text}"
        }
    }

    fun computeNullable(element: PsiElement): R? {
        element.accept(this)
        return result;
    }

    protected fun set(block: () -> R) {
        result = block()
    }
}
