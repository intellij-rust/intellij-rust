package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.RustCompletionEngine.KEYWORD_PRIORITY

class RustKeywordCompletionProvider(
    private vararg val keywords: String
) : CompletionProvider<CompletionParameters>() {
    val addSpaceHandler = InsertHandler<LookupElement> { context, el ->
        context.document.insertString(context.selectionEndOffset, " ")
        EditorModificationUtil.moveCaretRelatively(context.editor, 1)
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        for (keyword in keywords) {
            var builder = LookupElementBuilder.create(keyword)
            if (keyword in ADD_SPACE) {
                builder = builder.withInsertHandler(addSpaceHandler)
            }
            result.addElement(PrioritizedLookupElement.withPriority(builder, KEYWORD_PRIORITY))
        }
    }

    private companion object {
        val ADD_SPACE = listOf("crate", "enum", "extern", "fn", "impl", "let", "mod", "mut", "pub", "struct", "trait", "type", "unsafe", "use")
    }
}
