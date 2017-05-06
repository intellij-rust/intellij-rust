package org.rust.lang.core.stubs

import com.intellij.psi.stubs.IndexSink
import org.rust.lang.core.psi.ext.RsTypeAliasRole
import org.rust.lang.core.resolve.indexes.RsImplIndex
import org.rust.lang.core.stubs.index.RsGotoClassIndex
import org.rust.lang.core.stubs.index.RsMacroDefinitionIndex
import org.rust.lang.core.stubs.index.RsModulesIndex
import org.rust.lang.core.stubs.index.RsNamedElementIndex

fun IndexSink.indexExternCrate(stub: RsExternCrateItemStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexStructItem(stub: RsStructItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexEnumItem(stub: RsEnumItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
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
}

fun IndexSink.indexImplItem(stub: RsImplItemStub) {
    RsImplIndex.index(stub, this)
}

fun IndexSink.indexFunction(stub: RsFunctionStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexConstant(stub: RsConstantStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTypeAlias(stub: RsTypeAliasStub) {
    indexNamedStub(stub)
    if (stub.role != RsTypeAliasRole.IMPL_ASSOC_TYPE) {
        indexGotoClass(stub)
    }
}

fun IndexSink.indexFieldDecl(stub: RsFieldDeclStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexMacroDefinition(stub: RsMacroDefinitionStub) {
    stub.name?.let {
        occurrence(RsMacroDefinitionIndex.KEY, it)
    }
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
