package org.rust.lang.core.completion

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.RustCompletionEngine.KEYWORD_PRIORITY

class RustKeywordCompletionProvider(
    vararg keywords: String
) : CompletionProvider<CompletionParameters>() {
    val myKeywords: Array<out String>
    val addSpaceHandler = InsertHandler<LookupElement> { context, el ->
        context.document.insertString(context.selectionEndOffset, " ")
        EditorModificationUtil.moveCaretRelatively(context.editor, 1)
    }

    init {
        myKeywords = keywords
    }

    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        myKeywords.forEach { keyword ->
            var builder = LookupElementBuilder.create(keyword)
            if (keyword in ADD_SPACE) {
                builder = builder.withInsertHandler(addSpaceHandler)
            }
            result.addElement(PrioritizedLookupElement.withPriority(builder, KEYWORD_PRIORITY))
        }
    }

    private companion object {
        val ADD_SPACE = listOf("extern crate", "let", "mod", "use")
    }
}
