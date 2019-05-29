/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.stubs.IStubElementType
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider
import org.intellij.lang.regexp.RegExpLanguageHost
import org.intellij.lang.regexp.psi.RegExpChar
import org.intellij.lang.regexp.psi.RegExpGroup
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef
import org.rust.ide.injected.escaperForLiteral
import org.rust.lang.core.psi.RS_ALL_STRING_LITERALS
import org.rust.lang.core.psi.RsLitExpr
import org.rust.lang.core.psi.RsLiteralKind
import org.rust.lang.core.psi.impl.RsExprImpl
import org.rust.lang.core.psi.kind
import org.rust.lang.core.stubs.RsLitExprStub
import org.rust.lang.core.stubs.RsPlaceholderStub
import org.rust.lang.core.stubs.RsStubLiteralKind
import org.rust.lang.core.types.ty.TyFloat
import org.rust.lang.core.types.ty.TyInteger

val RsLitExpr.stubKind: RsStubLiteralKind?
    get() {
        val stub = (greenStub as? RsLitExprStub)
        if (stub != null) return stub.kind
        return when (val kind = kind) {
            is RsLiteralKind.Boolean -> RsStubLiteralKind.Boolean(kind.value)
            is RsLiteralKind.Char -> RsStubLiteralKind.Char(kind.value, kind.isByte)
            is RsLiteralKind.String -> RsStubLiteralKind.String(kind.value, kind.isByte)
            is RsLiteralKind.Integer -> RsStubLiteralKind.Integer(kind.value, TyInteger.fromSuffixedLiteral(integerLiteral!!))
            is RsLiteralKind.Float -> RsStubLiteralKind.Float(kind.value, TyFloat.fromSuffixedLiteral(floatLiteral!!))
            else -> null
        }
    }

val RsLitExpr.booleanValue: Boolean? get() = (stubKind as? RsStubLiteralKind.Boolean)?.value
val RsLitExpr.integerValue: Long? get() = (stubKind as? RsStubLiteralKind.Integer)?.value
val RsLitExpr.floatValue: Double? get() = (stubKind as? RsStubLiteralKind.Float)?.value
val RsLitExpr.charValue: String? get() = (stubKind as? RsStubLiteralKind.Char)?.value
val RsLitExpr.stringValue: String? get() = (stubKind as? RsStubLiteralKind.String)?.value

abstract class RsLitExprMixin : RsExprImpl, RsLitExpr, RegExpLanguageHost {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsPlaceholderStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun isValidHost(): Boolean =
        node.findChildByType(RS_ALL_STRING_LITERALS) != null

    override fun updateText(text: String): PsiLanguageInjectionHost {
        val valueNode = node.firstChildNode
        assert(valueNode is LeafElement)
        (valueNode as LeafElement).replaceWithText(text)
        return this
    }

    override fun createLiteralTextEscaper(): LiteralTextEscaper<RsLitExpr> =
        escaperForLiteral(this)

    override fun getReferences(): Array<PsiReference> =
        PsiReferenceService.getService().getContributedReferences(this)

    override fun characterNeedsEscaping(c: Char): Boolean = false
    override fun supportsPerl5EmbeddedComments(): Boolean = false
    override fun supportsPossessiveQuantifiers(): Boolean = true
    override fun supportsPythonConditionalRefs(): Boolean = false
    override fun supportsNamedGroupSyntax(group: RegExpGroup): Boolean = true

    override fun supportsNamedGroupRefSyntax(ref: RegExpNamedGroupRef): Boolean =
        ref.isNamedGroupRef

    override fun supportsExtendedHexCharacter(regExpChar: RegExpChar): Boolean = true

    override fun isValidCategory(category: String): Boolean =
        DefaultRegExpPropertiesProvider.getInstance().isValidCategory(category)

    override fun getAllKnownProperties(): Array<Array<String>> =
        DefaultRegExpPropertiesProvider.getInstance().allKnownProperties

    override fun getPropertyDescription(name: String?): String? =
        DefaultRegExpPropertiesProvider.getInstance().getPropertyDescription(name)

    override fun getKnownCharacterClasses(): Array<Array<String>> =
        DefaultRegExpPropertiesProvider.getInstance().knownCharacterClasses
}

fun RsLitExpr.containsOffset(offset: Int): Boolean =
    ElementManipulators.getValueTextRange(this).shiftRight(textOffset).containsOffset(offset)
