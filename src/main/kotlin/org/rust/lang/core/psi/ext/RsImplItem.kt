package org.rust.lang.core.psi.ext

import com.intellij.ide.projectView.PresentationData
import com.intellij.lang.ASTNode
import com.intellij.navigation.ItemPresentation
import com.intellij.psi.stubs.IStubElementType
import org.rust.ide.icons.RsIcons
import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.stubs.RsImplItemStub

abstract class RsImplItemImplMixin : RsStubbedElementImpl<RsImplItemStub>, RsImplItem {

    constructor(node: ASTNode) : super(node)
    constructor(stub: RsImplItemStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

    override fun getIcon(flags: Int) = RsIcons.IMPL

    override val isPublic: Boolean get() = false // pub does not affect imls at all

    override fun getPresentation(): ItemPresentation {
        val t = typeReference
        if (t is RsBaseType) {
            val pres = (t.path?.reference?.resolve() as? RsNamedElement)?.presentation
            if (pres != null) {
                return PresentationData(pres.presentableText, pres.locationString, RsIcons.IMPL, null)
            }
        }
        return PresentationData(typeReference?.text ?: "Impl", null, RsIcons.IMPL, null)
    }
}


fun RsImplItem.toImplementFunctions(): List<RsFunction> {
    val trait = traitRef?.trait ?: error("No trait ref")
    val canImplement = trait.functionList.associateBy { it.name }
    val mustImplement = canImplement.filterValues { it.isAbstract }
    val implemented = functionList.associateBy { it.name }
    val notImplemented = mustImplement.keys - implemented.keys
    val toImplement = trait.functionList.filter { it.name in notImplemented }

    return toImplement
}

fun RsImplItem.toImplementTypes(): List<RsTypeAlias> {
    val trait = traitRef?.trait ?: error("No trait ref")
    val canImplement = trait.typeAliasList.associateBy { it.name }
    val mustImplement = canImplement.filterValues { it.typeReference == null }
    val implemented = typeAliasList.associateBy { it.name }
    val notImplemented = mustImplement.keys - implemented.keys
    val toImplement = trait.typeAliasList.filter { it.name in notImplemented }

    return toImplement
}

fun RsImplItem.toImplementConstants(): List<RsConstant> {
    val trait = traitRef?.trait ?: error("No trait ref")
    val canImplement = trait.constantList.associateBy { it.name }
    val mustImplement = canImplement.filterValues { it.expr == null }
    val implemented = constantList.associateBy { it.name }
    val notImplemented = mustImplement.keys - implemented.keys
    val toImplement = trait.constantList.filter { it.name in notImplemented }

    return toImplement
}

fun RsImplItem.canOverrideFunctions() =
    traitRef?.trait?.functionList ?: error("No trait ref")

fun RsImplItem.canOverrideTypeAliases() =
    traitRef?.trait?.typeAliasList ?: error("No trait ref")

fun RsImplItem.canOverrideConstants() =
    traitRef?.trait?.constantList ?: error("No trait ref")
