/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.completion

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.util.ProcessingContext
import org.rust.lang.core.completion.RsLookupElementProperties.KeywordKind

class RsVisibilityCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val items = listOf(
            "pub" to KeywordKind.PUB,
            "pub(crate)" to KeywordKind.PUB_CRATE
        )
        for ((name, priority) in items) {
            result.addElement(
                LookupElementBuilder.create(name)
                    .bold()
                    .withInsertHandler { ctx, _ ->
                        insertSpaceIfNeeded(ctx)
                        ctx.editor.caretModel.moveToOffset(ctx.selectionEndOffset)
                    }
                    .toKeywordElement(priority)
            )
        }
        result.addElement(
            LookupElementBuilder.create("pub()")
                .bold()
                .withInsertHandler { ctx, _ ->
                    val offset = ctx.selectionEndOffset
                    insertSpaceIfNeeded(ctx)
                    ctx.editor.caretModel.moveToOffset(offset - 1)

                    // Trigger auto-completion for vis restriction
                    AutoPopupController.getInstance(ctx.project).scheduleAutoPopup(ctx.editor)
                }
                .toKeywordElement(KeywordKind.PUB_PARENS)
        )
    }
}

/**
 * Inserts a space after the completed item if it's not already present.
 */
private fun insertSpaceIfNeeded(ctx: InsertionContext) {
    val document = ctx.document
    if (document.charsSequence.getOrNull(ctx.selectionEndOffset) != ' ') {
        document.insertString(ctx.selectionEndOffset, " ")
    }
}
