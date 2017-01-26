package org.rust.lang.core.psi.impl

import org.rust.lang.core.psi.RsRefLikeType

val RsRefLikeType.isMut: Boolean get() = stub?.isMut ?: (mut != null)
val RsRefLikeType.isRef: Boolean get() = stub?.isMut ?: (and != null)
