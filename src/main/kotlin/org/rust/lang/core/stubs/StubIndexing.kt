package org.rust.lang.core.stubs

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.IndexSink
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.impl.mixin.RustTypeAliasRole
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.stubs.index.RustGotoClassIndex
import org.rust.lang.core.stubs.index.RustModulesIndex
import org.rust.lang.core.stubs.index.RustNamedElementIndex

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
    val pathKey = stub.pathAttribute?.let { FileUtil.getNameWithoutExtension(PathUtil.getFileName(it)) }
        ?: stub.name

    if (pathKey != null) {
        occurrence(RustModulesIndex.KEY, pathKey)
    }
}

fun IndexSink.indexModItem(stub: RsModItemStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTraitItem(stub: RsTraitItemStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexImplItem(stub: RsImplItemStub) {
    RustImplIndex.TraitImpls.index(stub, this)
    RustImplIndex.InherentImpls.index(stub, this)
}

fun IndexSink.indexFunction(stub: RsFunctionStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexConstant(stub: RsConstantStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTypeAlias(stub: RsTypeAliasStub) {
    indexNamedStub(stub)
    if (stub.role != RustTypeAliasRole.IMPL_ASSOC_TYPE) {
        indexGotoClass(stub)
    }
}

fun IndexSink.indexFieldDecl(stub: RsFieldDeclStub) {
    indexNamedStub(stub)
}

private fun IndexSink.indexNamedStub(stub: RustNamedStub) {
    stub.name?.let {
        occurrence(RustNamedElementIndex.KEY, it)
    }
}

private fun IndexSink.indexGotoClass(stub: RustNamedStub) {
    stub.name?.let {
        occurrence(RustGotoClassIndex.KEY, it)
    }
}
