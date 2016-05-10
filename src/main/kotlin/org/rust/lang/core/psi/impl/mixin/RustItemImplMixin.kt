package org.rust.lang.core.psi.impl.mixin

import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.*
import org.rust.lang.core.psi.impl.RustStubbedNamedElementImpl
import org.rust.lang.core.psi.impl.usefulName
import org.rust.lang.core.stubs.RustItemStub
import javax.swing.Icon

abstract class RustItemImplMixin : RustStubbedNamedElementImpl<RustItemStub>
                                 , RustItem {

    constructor(node: ASTNode) : super(node)

    constructor(stub: RustItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override val boundElements: Collection<RustNamedElement>
        get() = listOf(this)

    override val isPublic: Boolean
        get() = vis != null

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getLocationString(): String? = "(in ${containingFile.usefulName})"

        override fun getIcon(unused: Boolean): Icon? = this@RustItemImplMixin.getIcon(0)

        override fun getPresentableText(): String? = name
    }
}


val RustItem.queryAttributes: QueryAttributes get() = QueryAttributes(outerAttrList)

class QueryAttributes(private val outerAttributes: List<RustOuterAttr>) {
    fun findOuterAttr(name: String): RustOuterAttr? =
        outerAttributes.find { it.metaItem.identifier.textMatches(name) }

    fun hasAtomAttribute(name: String): Boolean =
        metaItems
            .filter { it.eq == null && it.lparen == null }
            .any { it.identifier.text == name}

    fun lookupStringValueForKey(key: String): String? =
        metaItems
            .filter { it.identifier.text == key }
            .mapNotNull { (it.litExpr?.stringLiteral as? RustLiteral.Text)?.value }
            .singleOrNull()


    //TODO: handle inner attributes here.
    private val metaItems: List<RustMetaItem> get() = outerAttributes.mapNotNull { it.metaItem }
}
