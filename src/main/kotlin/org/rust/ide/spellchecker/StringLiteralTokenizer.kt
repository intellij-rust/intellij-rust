package org.rust.ide.spellchecker

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.spellchecker.inspections.PlainTextSplitter
import com.intellij.spellchecker.tokenizer.EscapeSequenceTokenizer
import com.intellij.spellchecker.tokenizer.TokenConsumer
import org.rust.lang.utils.parseRustStringCharacters

class StringLiteralTokenizer : EscapeSequenceTokenizer<LeafPsiElement>() {

    override fun tokenize(element: LeafPsiElement, consumer: TokenConsumer) {
        val text = element.text

        if ("\\" !in text) {
            consumer.consumeToken(element, PlainTextSplitter.getInstance())
        } else {
            processTextWithEscapeSequences(element, text, consumer)
        }
    }

    companion object {
        fun processTextWithEscapeSequences(element: LeafPsiElement, text: String, consumer: TokenConsumer) {
            val unescapedText = StringBuilder()
            val offsets = IntArray(text.length + 1)
            parseRustStringCharacters(text, unescapedText, offsets)
            processTextWithOffsets(element, consumer, unescapedText, offsets, 0)
        }
    }
}
