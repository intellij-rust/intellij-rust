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
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext?, result: CompletionResultSet) {
        for (keyword in keywords) {
            var builder = LookupElementBuilder.create(keyword)
            SUFFIXES
                .filter { keyword in it.second }
                .firstOrNull()
                ?.let { builder = builder.withInsertHandler(it.first) }
            result.addElement(PrioritizedLookupElement.withPriority(builder, KEYWORD_PRIORITY))
        }
    }

    private companion object {
        val SUFFIXES = listOf(
            AddSuffix(" ") to listOf("crate", "const", "enum", "extern", "fn", "impl", "let", "mod", "mut", "pub",
                "static", "struct", "trait", "type", "unsafe", "use"),
            AddSuffix("::") to listOf("self", "super")
        )
    }

    private class AddSuffix(val suffix: String) : InsertHandler<LookupElement> {
        override fun handleInsert(context: InsertionContext, item: LookupElement) {
            context.document.insertString(context.selectionEndOffset, suffix)
            EditorModificationUtil.moveCaretRelatively(context.editor, suffix.length)
        }
    }
}
