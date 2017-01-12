package org.rust.lang.core.stubs

interface RsNamedStub {
    val name: String?
}

interface RsVisibilityStub {
    val isPublic: Boolean
}
