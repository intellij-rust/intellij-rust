package org.rust.lang.core.psi

import com.intellij.psi.PsiElement

/**
 * A value returning visitor designed to compute a function on a PsiElement
 *
 * Note that because [RsVisitor] is not designed to return a value,
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
abstract class RustComputingVisitor<R : Any>(default: R? = null) : RsVisitor() {
    private var result: R? = default

    fun compute(element: PsiElement): R {
        element.accept(this)
        return checkNotNull(result) {
            "Element $element was unhandled" +
                "\n${element.containingFile.virtualFile.path}" +
                "\n${element.text}"
        }
    }

    protected fun set(block: () -> R) {
        result = block()
    }
}
