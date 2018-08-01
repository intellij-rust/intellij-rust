/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.injected

import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import org.intellij.lang.regexp.RegExpLanguage
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.RsAbstractable
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.owner
import org.rust.lang.core.types.type

/**
 * Injects RegExpr language to a string literals in context like
 * `Regex::new("...")` and `RegexSet::new(&["...", "...", "..."])`
 */
class RsRegExpInjector : MultiHostInjector {
    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(RsLitExpr::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context.isValid && context is RsLitExpr) {
            val range = (context.kind as? RsLiteralKind.String)?.offsets?.value ?: return
            if (shouldInject(context)) {
                registrar.startInjecting(RegExpLanguage.INSTANCE)
                    .addPlace(null, null, context, range)
                    .doneInjecting()
            }
        }
    }

    private fun shouldInject(context: RsLitExpr): Boolean =
        isRegexNew(context) || isRegexSetNew(context)

    /** Regex::new("...") */
    private fun isRegexNew(context: RsLitExpr): Boolean {
//        val fn = ((context.parent?.parent as? RsCallExpr)?.expr as? RsPathExpr)
//            ?.path?.reference?.resolve() as? RsFunction
//            ?: return false
//        return fn.name == "new" && fn.implTypeName == "Regex"

        // We switched to this dumb implementation because this code is sometimes
        // called from EDT, and invoking `reference.resolve()` can freeze the UI.
        // See https://github.com/intellij-rust/intellij-rust/issues/2733
        val call = ((context.parent?.parent as? RsCallExpr)?.expr as? RsPathExpr) ?: return false
        return call.path.text == "Regex::new"
    }

    /** RegexSet::new(&["...", "...", "..."]) */
    private fun isRegexSetNew(context: RsLitExpr): Boolean {
        // use this logic for now because we can't resolve `RegexSet::new` (looks like it's implemented by a macro)
        val call = (context.parent?.parent?.parent?.parent as? RsCallExpr)?.expr as? RsPathExpr ?: return false
        return call.path.text == "RegexSet::new"
    }
}

/** For fn `bar` in `impl Foo { fn bar() {} }` returns "Foo" string */
private val RsAbstractable.implTypeName: String?
    get() = (owner as? RsAbstractableOwner.Impl)?.impl?.typeReference?.type?.toString()
