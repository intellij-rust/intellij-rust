/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.doc.psi

import com.intellij.psi.PsiLanguageInjectionHost
import org.rust.lang.core.psi.RsDocCommentImpl2
import org.rust.lang.core.psi.ext.RsElement
import org.rust.lang.core.psi.ext.ancestorStrict

interface RsDocElement : RsElement

val RsDocElement.owner: RsElement
    get() = ancestorStrict<RsDocCommentImpl2>()!!.parent as RsElement

val RsDocElement.ownerDoc: RsDocCommentImpl2
    get() = ancestorStrict<RsDocCommentImpl2>()!!

interface RsDocLink : RsDocElement {
    val linkTextOrLabel: RsDocElement
}

/**
 * ```
 * [link text](link_destination)
 * ```
 */
interface RsDocInlineLink : RsDocLink {
    val linkText: RsDocLinkText
    val linkDestination: RsDocLinkDestination

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkText
}

/**
 * ```
 * [link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkReferenceDef]
 */
interface RsDocLinkReferenceShort : RsDocLink {
    val linkLabel: RsDocLinkLabel

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkLabel
}

/**
 * ```
 * [link text][link label]
 * ```
 *
 * Then, the link should be defined with [RsDocLinkReferenceDef] (identified by [linkLabel])
 */
interface RsDocLinkReferenceFull : RsDocLink {
    val linkText: RsDocLinkText
    val linkLabel: RsDocLinkLabel

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkText
}

/**
 * ```
 * [link label]: link_destination
 * ```
 */
interface RsDocLinkReferenceDef : RsDocLink {
    val linkLabel: RsDocLinkLabel
    val linkDestination: RsDocLinkDestination

    @JvmDefault
    override val linkTextOrLabel: RsDocElement
        get() = linkLabel
}

interface RsDocLinkText : RsDocElement
interface RsDocLinkLabel : RsDocElement
interface RsDocLinkDestination : RsDocElement

interface RsDocCodeFence : RsDocElement, PsiLanguageInjectionHost
