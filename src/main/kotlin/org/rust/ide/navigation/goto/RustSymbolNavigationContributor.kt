package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RustNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

class RustSymbolNavigationContributor
    : RustNavigationContributorBase<RustNamedElement>(RustNamedElementIndex.KEY, RustNamedElement::class.java)
