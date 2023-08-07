/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.debugger.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.util.Predicates
import com.intellij.psi.PsiElement
import com.intellij.psi.SyntaxTraverser
import com.intellij.xdebugger.XExpression
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.ext.*
import org.rust.stdext.enumSetOf
import java.util.*
import java.util.function.Predicate

@Suppress("UnstableApiUsage")
class RsDebuggerUsageCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup {
        return GROUP
    }

    companion object {
        private val GROUP = EventLogGroup("rust.debug.evaluate.expression", 4)

        private val SUCCESS = EventFields.Boolean("success")
        private val DEBUGGER_KIND = EventFields.Enum<DebuggerKind>("debugger_kind")

        private val EXPRESSION_EVALUATED = GROUP.registerEvent("evaluated", SUCCESS, DEBUGGER_KIND)
        private val ELEMENT_USED_IN_EVALUATION = GROUP.registerEvent("element.used", SUCCESS, DEBUGGER_KIND, EventFields.Enum("element", ExpressionKind::class.java))

        fun logEvaluated(success: Boolean, debuggerKind: DebuggerKind, features: EnumSet<ExpressionKind>) {
            EXPRESSION_EVALUATED.log(success, debuggerKind)
            for (feature in features) {
                ELEMENT_USED_IN_EVALUATION.log(success, debuggerKind, feature)
            }
        }

        fun collectUsedElements(expr: XExpression, context: RsFile): EnumSet<ExpressionKind> {
            val project = context.project
            val codeFragment = RsExpressionCodeFragment(project, expr.expression, context)
            val features = enumSetOf<ExpressionKind>()
            for (element in SyntaxTraverser.psiTraverser(codeFragment)) {
                for (value in RsDebuggerUsageCollector.ExpressionKind.values()) {
                    if (value.predicate.test(element)) {
                        features.add(value)
                    }
                }
            }
            if (features.isEmpty()) {
                features.add(ExpressionKind.Unknown)
            }
            return features
        }

        private fun isInherentImplMethodCall(element: PsiElement): Boolean =
            element is RsMethodCall && (element.reference.resolve() as? RsAbstractable)?.owner?.isInherentImpl == true

        private fun isTraitMethodCall(element: PsiElement): Boolean {
            if (element !is RsMethodCall) return false
            val owner = (element.reference.resolve() as? RsAbstractable)?.owner ?: return false
            return owner is RsAbstractableOwner.Trait || owner.isTraitImpl
        }

        private fun isTypeQualifiedPath(element: PsiElement): Boolean {
            if (element !is RsPath) return false
            return element.typeQual != null || element.qualifier?.reference?.resolve() is RsTypeDeclarationElement
        }

        private fun isPathToGenericItem(element: PsiElement): Boolean {
            if (element !is RsPath) return false
            val resolvedTo = element.reference?.resolve() ?: return false
            return resolvedTo is RsGenericDeclaration && resolvedTo.typeParameters.isNotEmpty()
        }

        private fun isUnresolvedReference(element: PsiElement): Boolean {
            if (element !is RsReferenceElement) return false
            val reference = element.reference ?: return false
            return reference.resolve() == null
        }
    }

    enum class ExpressionKind(val predicate: Predicate<PsiElement>) {
        MethodCall(RsMethodCall::class.java::isInstance),
        InherentImplMethodCall(::isInherentImplMethodCall),
        TraitImplMethodCall(::isTraitMethodCall),
        FunctionCall(RsCallExpr::class.java::isInstance),
        TypeQualifiedPath(::isTypeQualifiedPath),
        PathToGenericItem(::isPathToGenericItem),
        MacroCall(RsMacroCall::class.java::isInstance),
        Lambda(RsLambdaExpr::class.java::isInstance),
        UnresolvedReference(::isUnresolvedReference),
        Unknown(Predicates.alwaysFalse())
    }

    enum class DebuggerKind {
        GDB, LLDB, Unknown
    }
}

