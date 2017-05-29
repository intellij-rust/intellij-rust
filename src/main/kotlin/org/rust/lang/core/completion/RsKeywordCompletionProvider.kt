package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.CompletionEngine.KEYWORD_PRIORITY
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.ext.parentOfType
import org.rust.lang.core.psi.ext.returnType
import org.rust.lang.core.types.ty.TyUnit

class RsKeywordCompletionProvider(
    private vararg val keywords: String
) : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        for (keyword in keywords) {
            var builder = LookupElementBuilder.create(keyword)
            builder = addInsertionHandler(keyword, builder, parameters)
            result.addElement(PrioritizedLookupElement.withPriority(builder, KEYWORD_PRIORITY))
        }
    }
}

class AddSuffixInsertionHandler(val suffix: String) : InsertHandler<LookupElement> {
    override fun handleInsert(context: InsertionContext, item: LookupElement) {
        context.document.insertString(context.selectionEndOffset, suffix)
        EditorModificationUtil.moveCaretRelatively(context.editor, suffix.length)
    }
}

private val ALWAYS_NEEDS_SPACE = setOf("crate", "const", "enum", "extern", "fn", "impl", "let", "mod", "mut", "pub",
    "static", "struct", "trait", "type", "unsafe", "use")


private fun addInsertionHandler(keyword: String, builder: LookupElementBuilder, parameters: CompletionParameters): LookupElementBuilder {
    val suffix = when (keyword) {
        in ALWAYS_NEEDS_SPACE -> " "
        "return" -> {
            val fn = parameters.position.parentOfType<RsFunction>() ?: return builder
            if (fn.returnType !is TyUnit) " " else ";"
        }
        else -> return builder
    }

    return builder.withInsertHandler(AddSuffixInsertionHandler(suffix))
}
