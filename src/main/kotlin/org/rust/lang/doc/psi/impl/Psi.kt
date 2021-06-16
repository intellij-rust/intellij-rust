/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi.impl

import com.intellij.lang.psi.SimpleMultiLineTextEscaper
import com.intellij.psi.LiteralTextEscaper
import com.intellij.psi.PsiLanguageInjectionHost
import com.intellij.psi.impl.source.tree.AstBufferUtil
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.text.CharArrayUtil
import org.rust.ide.injected.RsDoctestLanguageInjector
import org.rust.lang.core.psi.RsPsiFactory
import org.rust.lang.core.psi.ext.ancestorStrict
import org.rust.lang.core.psi.ext.childOfType
import org.rust.lang.core.psi.ext.elementType
import org.rust.lang.core.psi.ext.getPrevNonWhitespaceSibling
import org.rust.lang.doc.psi.*

abstract class RsDocElementImpl(type: IElementType) : CompositePsiElement(type), RsDocElement {
    protected open fun <T: Any> notNullChild(child: T?): T =
        child ?: error("$text parent=${parent.text}")

    override val containingDoc: RsDocComment
        get() = ancestorStrict()
            ?: error("RsDocElement cannot leave outside of the doc comment! `${text}`")

    override val markdownValue: String
        get() = AstBufferUtil.getTextSkippingWhitespaceComments(this)

    override fun toString(): String = "${javaClass.simpleName}($elementType)"
}

class RsDocGapImpl(type: IElementType, val text: CharSequence) : LeafPsiElement(type, text), RsDocGap {
    override fun getTokenType(): IElementType = elementType
}

class RsDocAtxHeadingImpl(type: IElementType) : RsDocElementImpl(type), RsDocAtxHeading
class RsDocSetextHeadingImpl(type: IElementType) : RsDocElementImpl(type), RsDocSetextHeading

class RsDocEmphasisImpl(type: IElementType) : RsDocElementImpl(type), RsDocEmphasis
class RsDocStrongImpl(type: IElementType) : RsDocElementImpl(type), RsDocStrong
class RsDocCodeSpanImpl(type: IElementType) : RsDocElementImpl(type), RsDocCodeSpan
class RsDocAutoLinkImpl(type: IElementType) : RsDocElementImpl(type), RsDocAutoLink

class RsDocInlineLinkImpl(type: IElementType) : RsDocElementImpl(type), RsDocInlineLink {
    override val linkText: RsDocLinkText
        get() = notNullChild(childOfType())

    override val linkDestination: RsDocLinkDestination
        get() = notNullChild(childOfType())
}

class RsDocLinkReferenceShortImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkReferenceShort {
    override val linkLabel: RsDocLinkLabel
        get() = notNullChild(childOfType())
}

class RsDocLinkReferenceFullImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkReferenceFull {
    override val linkText: RsDocLinkText
        get() = notNullChild(childOfType())

    override val linkLabel: RsDocLinkLabel
        get() = notNullChild(childOfType())
}

class RsDocLinkDefinitionImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkDefinition {
    override val linkLabel: RsDocLinkLabel
        get() = notNullChild(childOfType())

    override val linkDestination: RsDocLinkDestination
        get() = notNullChild(childOfType())
}

class RsDocLinkTextImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkText
class RsDocLinkLabelImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkLabel
class RsDocLinkTitleImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkTitle
class RsDocLinkDestinationImpl(type: IElementType) : RsDocElementImpl(type), RsDocLinkDestination

class RsDocCodeFenceImpl(type: IElementType) : RsDocElementImpl(type), RsDocCodeFence {
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
        val docKind = RsDocKind.of(containingDoc.elementType)
        val infix = docKind.infix

        val prevSibling = getPrevNonWhitespaceSibling() // Should be an `infix` (e.g. `///`)

        val newText = StringBuilder()

        // `newText` must be parsed in an empty file, so append a prefix if it differs from `infix` (e.g. `/**`)
        if (prevSibling?.text != docKind.prefix) {
            newText.append(docKind.prefix)
            newText.append("\n")
        }

        newText.append(docKind.infix)

        // Add a whitespace between `infix` and backticks (e.g. "///" and "```").
        // The whitespace affects markdown escaping, hence markdown parsing
        if (prevSibling != null && prevSibling.nextSibling != this) {
            newText.append(prevSibling.nextSibling.text)
        }

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

        if (docKind.isBlock && !newText.endsWith("*/")) {
            newText.append("\n*/")
        }

        // There are some problems with indentation if we just use replaceWithText(text).
        // copied from PsiCommentManipulator
        val fromText = RsPsiFactory(project, markGenerated = true).createFile(newText)
        val newElement = PsiTreeUtil.findChildOfType(fromText, javaClass, false)
            ?: error(newText)
        return replace(newElement) as RsDocCodeFenceImpl
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<RsDocCodeFenceImpl> =
        SimpleMultiLineTextEscaper(this)
}

class RsDocCodeBlockImpl(type: IElementType) : RsDocElementImpl(type), RsDocCodeBlock
class RsDocHtmlBlockImpl(type: IElementType) : RsDocElementImpl(type), RsDocHtmlBlock

class RsDocCodeFenceStartEndImpl(type: IElementType) : RsDocElementImpl(type), RsDocCodeFenceStartEnd
class RsDocCodeFenceLangImpl(type: IElementType) : RsDocElementImpl(type), RsDocCodeFenceLang
