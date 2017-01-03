package org.rust.lang.core.stubs

import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.stubs.IndexSink
import com.intellij.util.PathUtil
import org.rust.lang.core.psi.impl.mixin.RustTypeAliasRole
import org.rust.lang.core.resolve.indexes.RustAliasIndex
import org.rust.lang.core.resolve.indexes.RustImplIndex
import org.rust.lang.core.stubs.index.RustGotoClassIndex
import org.rust.lang.core.stubs.index.RustModulesIndex
import org.rust.lang.core.stubs.index.RustNamedElementIndex

fun IndexSink.indexExternCrate(stub: RustExternCrateItemElementStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexStructItem(stub: RustStructItemElementStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexUnionItem(stub: RustUnionItemElementStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexEnumItem(stub: RustEnumItemElementStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexModDeclItem(stub: RustModDeclItemElementStub) {
    indexNamedStub(stub)
    val pathKey = stub.pathAttribute?.let { FileUtil.getNameWithoutExtension(PathUtil.getFileName(it)) }
        ?: stub.name

    if (pathKey != null) {
        occurrence(RustModulesIndex.KEY, pathKey)
    }
}

fun IndexSink.indexModItem(stub: RustModItemElementStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTraitItem(stub: RustTraitItemElementStub) {
    indexNamedStub(stub)
    indexGotoClass(stub)
}

fun IndexSink.indexImplItem(stub: RustImplItemElementStub) {
    RustImplIndex.ByType.index(stub, this)
    RustImplIndex.ByName.index(stub, this)
}

fun IndexSink.indexFunction(stub: RustFunctionElementStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexConstant(stub: RustConstantElementStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexTypeAlias(stub: RustTypeAliasElementStub) {
    indexNamedStub(stub)
    if (stub.role != RustTypeAliasRole.IMPL_ASSOC_TYPE) {
        indexGotoClass(stub)
    }
}

fun IndexSink.indexFieldDecl(stub: RustFieldDeclElementStub) {
    indexNamedStub(stub)
}

fun IndexSink.indexAlias(stub: RustAliasElementStub) {
    stub.name?.let {
        occurrence(RustAliasIndex.KEY, it)
    }
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
