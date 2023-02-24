/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import org.rust.lang.core.psi.ext.RsAbstractableOwner
import org.rust.lang.core.psi.ext.ownerBySyntaxOnly
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.resolve.indexes.RsLangItemIndex
import org.rust.lang.core.resolve.indexes.RsMacroIndex
import org.rust.lang.core.resolve.indexes.RsTypeAliasIndex
import org.rust.lang.core.stubs.index.*

fun IndexSink.indexExternCrate(stub: RsExternCrateItemStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexStructItem(stub: RsStructItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
    RsLangItemIndex.index(stub.psi, this)
}

fun IndexSink.indexEnumItem(stub: RsEnumItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexEnumVariant(stub: RsEnumVariantStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexModDeclItem(stub: RsModDeclItemStub) {
    indexNamedStub(stub)
    RsModulesIndex.index(stub, this)
}

fun IndexSink.indexModItem(stub: RsModItemStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTraitItem(stub: RsTraitItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
    RsLangItemIndex.index(stub.psi, this)
}

fun IndexSink.indexImplItem(stub: RsImplItemStub) {
    RsImplIndex.index(stub, this)
}

fun IndexSink.indexTraitAlias(stub: RsTraitAliasStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
    RsLangItemIndex.index(stub.psi, this)
}

fun IndexSink.indexFunction(stub: RsFunctionStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexConstant(stub: RsConstantStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTypeAlias(stub: RsTypeAliasStub) {
    indexNamedStub(stub)
    if (stub.psi.ownerBySyntaxOnly !is RsAbstractableOwner.Impl) {
        indexGotoClass(stub)
        RsTypeAliasIndex.index(stub, this)
    }
}

fun IndexSink.indexNamedFieldDecl(stub: RsNamedFieldDeclStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexMacro(stub: RsMacroStub) {
    indexNamedStub(stub)
    RsMacroIndex.index(stub, this)
}

fun IndexSink.indexMacroDef(stub: RsMacro2Stub) {
    indexNamedStub(stub)
}

fun IndexSink.indexInnerAttr(stub: RsInnerAttrStub) {
    RsFeatureIndex.index(stub, this)
}

fun IndexSink.indexMetaItem(stub: RsMetaItemStub) {
    RsCfgNotTestIndex.index(stub, this)
}

private fun IndexSink.indexNamedStub(stub: RsNamedStub) {
    stub.name?.let {
        occurrence(RsNamedElementIndex.KEY, it)
    }
}

private fun IndexSink.indexGotoClass(stub: RsNamedStub) {
    stub.name?.let {
        occurrence(RsGotoClassIndex.KEY, it)
    }
}
