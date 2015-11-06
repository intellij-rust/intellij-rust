package org.rust.lang.spellchecker

import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer
import com.intellij.spellchecker.tokenizer.TokenConsumer
import org.rust.lang.core.psi.RustLitExpr

class LiteralExpressionTokenizer : EscapeSequenceTokenizer<RustLitExpr>() {

    override fun tokenize(element: RustLitExpr, consumer: TokenConsumer) {
        val text = element.stringLiteral?.text ?: return

        if ("\\" !in text) {
            consumer.consumeToken(element, PlainTextSplitter.getInstance())
        } else {
            processTextWithEscapeSequences(element, text, consumer)
        }
    }

    companion object {
        fun processTextWithEscapeSequences(element: RustLitExpr, text: String, consumer: TokenConsumer) {
            val unescapedText = StringBuilder()
            val offsets = IntArray(text.length + 1)
            PsiLiteralExpressionImpl.parseStringCharacters(text, unescapedText, offsets)

            processTextWithOffsets(element, consumer, unescapedText, offsets, 1)
        }
    }
}
