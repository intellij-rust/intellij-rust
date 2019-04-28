/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import com.intellij.lang.ASTNode
import com.intellij.psi.stubs.IStubElementType
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.RsUseGroup
import org.rust.lang.core.psi.RsUseSpeck
import org.rust.lang.core.stubs.RsUseSpeckStub

val RsUseSpeck.isStarImport: Boolean get() = greenStub?.isStarImport ?: (mul != null) // I hate operator precedence
val RsUseSpeck.qualifier: RsPath? get() {
    val parentUseSpeck = (context as? RsUseGroup)?.parentUseSpeck ?: return null
    return parentUseSpeck.pathOrQualifier
}

val RsUseSpeck.pathOrQualifier: RsPath? get() = path ?: qualifier

val RsUseSpeck.nameInScope: String? get() {
    if (useGroup != null) return null
    alias?.name?.let { return it }
    val baseName = path?.referenceName ?: return null
    if (baseName == "self") {
        return qualifier?.referenceName
    }
    return baseName
}

fun RsUseSpeck.forEachLeafSpeck(consumer: (RsUseSpeck) -> Unit) {
    val group = useGroup
    if (group == null) consumer(this) else group.useSpeckList.forEach { it.forEachLeafSpeck(consumer) }
}

abstract class RsUseSpeckImplMixin : RsStubbedElementImpl<RsUseSpeckStub>, RsUseSpeck {
    constructor (node: ASTNode) : super(node)
    constructor (stub: RsUseSpeckStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}
