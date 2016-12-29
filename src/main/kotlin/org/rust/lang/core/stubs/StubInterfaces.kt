package org.rust.lang.core.stubs

interface RustNamedStub {
    val name: String?
}

interface RustVisibilityStub {
    val isPublic: Boolean
}
