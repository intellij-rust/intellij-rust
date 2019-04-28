/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsBaseType
import org.rust.lang.core.psi.RsElementTypes.*
import org.rust.lang.core.psi.RsPath
import org.rust.lang.core.psi.tokenSetOf

val RsBaseType.name: String? get() = path?.referenceName

val RsBaseType.kind: RsBaseTypeKind
    get() = when (stubKind) {
        RsBaseTypeStubKind.UNIT -> RsBaseTypeKind.Unit
        RsBaseTypeStubKind.NEVER -> RsBaseTypeKind.Never
        RsBaseTypeStubKind.UNDERSCORE -> RsBaseTypeKind.Underscore
        RsBaseTypeStubKind.PATH -> RsBaseTypeKind.Path(path ?: error("Malformed RsBaseType element: `$text`"))
    }

sealed class RsBaseTypeKind {
    object Unit : RsBaseTypeKind()
    object Never : RsBaseTypeKind()
    object Underscore : RsBaseTypeKind()
    data class Path(val path: RsPath) : RsBaseTypeKind()
}

private val RS_BASE_TYPE_KINDS = tokenSetOf(LPAREN, EXCL, UNDERSCORE, PATH)

val RsBaseType.stubKind: RsBaseTypeStubKind get() {
    val stub = greenStub
    if (stub != null) return stub.kind

    val child = node.findChildByType(RS_BASE_TYPE_KINDS)
    return when (child?.elementType) {
        LPAREN -> RsBaseTypeStubKind.UNIT
        EXCL -> RsBaseTypeStubKind.NEVER
        UNDERSCORE -> RsBaseTypeStubKind.UNDERSCORE
        PATH -> RsBaseTypeStubKind.PATH
        else -> error("Malformed RsBaseType element: `$text`")
    }
}

enum class RsBaseTypeStubKind {
    UNIT, NEVER, UNDERSCORE, PATH;
}
