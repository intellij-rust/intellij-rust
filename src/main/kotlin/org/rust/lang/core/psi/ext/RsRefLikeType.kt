package org.rust.lang.core.psi.ext

import org.rust.lang.core.psi.RsRefLikeType

val RsRefLikeType.isMut: Boolean get() = stub?.isMut ?: (mut != null)
val RsRefLikeType.isRef: Boolean get() = stub?.isRef ?: (and != null)
