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
    get() = when (kindEnum) {
        RsBaseTypeKindEnum.UNIT -> RsBaseTypeKind.BtUnit
        RsBaseTypeKindEnum.NEVER -> RsBaseTypeKind.BtNever
        RsBaseTypeKindEnum.UNDERSCORE -> RsBaseTypeKind.BtUnderscore
        RsBaseTypeKindEnum.PATH -> RsBaseTypeKind.BtPath(path ?: error("Malformed RsBaseType element: `$text`"))
    }

sealed class RsBaseTypeKind {
    object BtUnit : RsBaseTypeKind()
    object BtNever : RsBaseTypeKind()
    object BtUnderscore : RsBaseTypeKind()
    data class BtPath(val path: RsPath) : RsBaseTypeKind()
}

private val RS_BASE_TYPE_KINDS = tokenSetOf(LPAREN, EXCL, UNDERSCORE, PATH)

val RsBaseType.kindEnum: RsBaseTypeKindEnum get() {
    val stub = stub
    if (stub != null) return stub.kind

    val child = node.findChildByType(RS_BASE_TYPE_KINDS)
    return when (child?.elementType) {
        LPAREN -> RsBaseTypeKindEnum.UNIT
        EXCL -> RsBaseTypeKindEnum.NEVER
        UNDERSCORE -> RsBaseTypeKindEnum.UNDERSCORE
        PATH -> RsBaseTypeKindEnum.PATH
        else -> error("Malformed RsBaseType element: `$text`")
    }
}

enum class RsBaseTypeKindEnum {
    UNIT, NEVER, UNDERSCORE, PATH;
}
