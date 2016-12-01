package org.rust.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.util.parentOfType

/**
 * Provides funcions/methods afruments hint.
 */
class RustParameterInfoHandler : ParameterInfoHandler<PsiElement, RustArgumentsDescription> {

    var hintText: String = ""

    override fun couldShowInLookup() = true

    override fun tracksParameterIndex() = true

    override fun getParameterCloseChars() = ",)"

    override fun getParametersForLookup(item: LookupElement, context: ParameterInfoContext?): Array<out Any> {
        val el = item.`object` as PsiElement
        val p = el.parent?.parent
        return if (p is RustCallExprElement && p.declaration != null || p is RustMethodCallExprElement && p.declaration != null) arrayOf(p) else emptyArray()
    }

    override fun getParametersForDocumentation(p: RustArgumentsDescription, context: ParameterInfoContext?) =
        p.arguments

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiElement? {
        val contextElement = context.file.findElementAt(context.editor.caretModel.offset) ?: return null
        return findElementForParameterInfo(contextElement)
    }

    fun findElementForParameterInfo(contextElement: PsiElement) =
        PsiTreeUtil.getParentOfType(contextElement, RustArgListElement::class.java)

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext) =
        context.file.findElementAt(context.editor.caretModel.offset)

    override fun showParameterInfo(element: PsiElement, context: CreateParameterInfoContext) {
        if (element !is RustArgListElement) return
        val argsDescr = RustArgumentsDescription.findDescription(element) ?: return
        context.itemsToShow = arrayOf(argsDescr)
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun updateParameterInfo(place: PsiElement, context: UpdateParameterInfoContext) {
        val argIndex = findArgumentIndex(place)
        if (argIndex == INVALID_INDEX) {
            context.removeHint()
            return
        }
        context.setCurrentParameter(argIndex)
        if (context.parameterOwner == null) {
            context.parameterOwner = place
        } else if (context.parameterOwner != findElementForParameterInfo(place)) {
            context.removeHint()
            return
        }
        context.objectsToView.mapIndexed { i, o -> context.setUIComponentEnabled(i, true) }
    }

    override fun updateUI(p: RustArgumentsDescription?, context: ParameterInfoUIContext) {
        if (p == null) {
            context.isUIComponentEnabled = false
            return
        }
        val range = p.getArgumentRange(context.currentParameterIndex)
        hintText = p.presentText
        context.setupUIComponentPresentation(
            hintText,
            range.startOffset,
            range.endOffset,
            !context.isUIComponentEnabled,
            false,
            false,
            context.defaultParameterColor)
    }

    /**
     * Finds index of the argument in the given place
     */
    private fun findArgumentIndex(place: PsiElement): Int {
        val callArgs = place.parentOfType<RustArgListElement>() ?: return INVALID_INDEX
        val descr = RustArgumentsDescription.findDescription(callArgs) ?: return INVALID_INDEX
        var index = -1
        if (descr.arguments.isNotEmpty()) {
            index += generateSequence(callArgs.firstChild, { c -> c.nextSibling})
                .filter { it.text == "," }
                .takeWhile { it.textRange.startOffset < place.textRange.startOffset }
                .count() + 1
            if (index >= descr.arguments.size) {
                index = -1
            }
        }
        return index
    }

    private companion object {
        val INVALID_INDEX: Int = -2
    }
}

/**
 * Holds information about arguments from func/method declaration
 */
class RustArgumentsDescription(
    val arguments: Array<RustArgumentDescription>
) {
    fun getArgumentRange(index: Int): TextRange {
        if (index < 0 || index >= arguments.size) return TextRange.EMPTY_RANGE
        val start = (0..index - 1).sumBy { arguments[it].textLen + 2 }
        val range = TextRange(start, start + arguments[index].textLen)
        return range
    }

    val presentText = if (arguments.isEmpty()) "<no arguments>" else arguments.joinToString(", ")

    companion object {
        /**
         * Finds declaration of the func/method and creates description of its arguments
         */
        fun findDescription(args: RustArgListElement): RustArgumentsDescription? {
            val call = args.parent
            val decl = when (call) {
                is RustCallExprElement -> call.declaration
                is RustMethodCallExprElement -> call.declaration
                else -> null
            } ?: return null
            val paramsList = when (decl) {
                is RustFnItemElement -> decl.parameters
                is RustImplMethodMemberElement -> decl.parameters
                else -> null
            }?.parameterList
            val params = paramsList
                ?.map { RustArgumentDescription(it.pat?.text, it.type?.text) }
                ?.toTypedArray()
            return RustArgumentsDescription(params ?: emptyArray())
        }
    }
}

class RustArgumentDescription(
    val name: String?,
    val type: String?
) {
    val textLen = (name?.length ?: 1) + 2 + (type?.length ?: 1)
    override fun toString() = (name ?: "?") + ": " + (type ?: "?")
}

private val RustCallExprElement.declaration: RustFnElement?
    get() = (expr as? RustPathExprElement)?.path?.reference?.resolve() as? RustFnElement

private val RustMethodCallExprElement.declaration: RustImplMethodMemberElement?
    get() = reference.resolve() as? RustImplMethodMemberElement
