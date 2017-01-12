package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RsNamedElement
import org.rust.lang.core.stubs.index.RustNamedElementIndex

class RsSymbolNavigationContributor
    : RsNavigationContributorBase<RsNamedElement>(RustNamedElementIndex.KEY, RsNamedElement::class.java)
