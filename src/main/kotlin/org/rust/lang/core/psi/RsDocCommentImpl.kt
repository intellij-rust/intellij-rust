/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi

import com.intellij.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.PsiCommentImpl
import com.intellij.psi.impl.source.tree.injected.InjectionBackgroundSuppressor
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import org.rust.ide.annotator.RsDoctestAnnotator
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.lang.core.completion.getOriginalOrSelf
import org.rust.lang.core.psi.ext.*
import org.rust.lang.doc.psi.RsDocKind

/**
 * Psi element for rust documentation comments. Provides specific behavior for
 * language injections (see [RsDoctestLanguageInjector]).
 * [InjectionBackgroundSuppressor] is used to disable builtin background highlighting for injection.
 * We create such background manually by [RsDoctestAnnotator] (see the class docs)
 */
class RsDocCommentImpl(type: IElementType, text: CharSequence) : PsiCommentImpl(type, text),
                                                                 PsiDocCommentBase,
                                                                 PsiLanguageInjectionHost,
                                                                 InjectionBackgroundSuppressor {
    override fun isValidHost(): Boolean = true

    /**
     * Handles changes in PSI injected to the comment (see [RsDoctestLanguageInjector]).
     * It is not used on typing. Instead, it's used on direct PSI changes (performed by
     * intentions/quick fixes).
     *
     * Each line of doc comment should start with some prefix (see [RsDocKind.infix]). For example, with `///`.
     * But if some intention inserts newline to PSI, there will not be such prefix after that newline.
     * Here we insure that every comment line is started from appropriate prefix
     */
    override fun updateText(text: String): PsiLanguageInjectionHost {
        val docKind = RsDocKind.of(elementType)
        val infix = docKind.infix

        val newText = StringBuilder()
        var prevIndent = ""
        var index = 0
        while (index < text.length) {
            val linebreakIndex = text.indexOf("\n", index)
            if (linebreakIndex == -1) {
                newText.append(text, index, text.length)
                break
            } else {
                val nextLineStart = linebreakIndex + 1
                newText.append(text, index, nextLineStart)
                index = nextLineStart

                val firstNonWhitespace = CharArrayUtil.shiftForward(text, nextLineStart, " \t")
                if (firstNonWhitespace == text.length) continue
                val isStartCorrect = text.startsWith(infix, firstNonWhitespace) ||
                    docKind.isBlock && text.startsWith("*/", firstNonWhitespace)
                if (!isStartCorrect) {
                    newText.append(prevIndent)
                    newText.append(infix)
                    newText.append(" ")
                } else {
                    prevIndent = text.substring(nextLineStart, firstNonWhitespace)
                }
            }
        }

        // There are some problems with indentation if we just use replaceWithText(text).
        // copied from PsiCommentManipulator
        val type = containingFile.fileType
        val fromText = PsiFileFactory.getInstance(project).createFileFromText("__." + type.defaultExtension, type, newText)
        val newElement = PsiTreeUtil.getParentOfType(fromText.findElementAt(0), javaClass, false)
            ?: error(type.toString() + " " + type.defaultExtension + " " + newText)
        return replace(newElement) as RsDocCommentImpl
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<PsiCommentImpl> =
        SimpleMultiLineTextEscaper(this)

    override fun getOwner(): RsDocAndAttributeOwner? = ancestorStrict()
}
