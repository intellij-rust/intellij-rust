package org.rust.lang.core.stubs

interface RustNamedStub {
    val name: String?
}

interface RustVisibilityStub {
    val isPublic: Boolean
}

interface RustFnStub {
    val isAbstract: Boolean
    val isStatic: Boolean
    val isTest: Boolean
}
