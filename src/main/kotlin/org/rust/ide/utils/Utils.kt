package org.rust.ide.utils

import com.intellij.openapi.util.io.StreamUtil
import org.rust.lang.core.psi.RsFunction
import org.rust.lang.core.psi.RsImplItem
import org.rust.lang.core.psi.RsTypeAlias
import org.rust.lang.core.psi.impl.mixin.isAbstract
import org.rust.lang.core.psi.util.trait

fun Collection<*>?.isNullOrEmpty() = this == null || isEmpty()

fun Any.loadCodeSampleResource(resource: String): String {
    val stream = javaClass.classLoader.getResourceAsStream(resource)
    // We need to convert line separators here, because IntelliJ always expects \n,
    // while on Windows the resource file will be read with \r\n as line separator.
    return StreamUtil.convertSeparators(StreamUtil.readText(stream, "UTF-8"))
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
