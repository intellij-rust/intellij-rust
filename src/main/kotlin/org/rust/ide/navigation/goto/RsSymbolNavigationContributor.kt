package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.ext.RsNamedElement
import org.rust.lang.core.stubs.index.RsNamedElementIndex

class RsSymbolNavigationContributor
    : RsNavigationContributorBase<RsNamedElement>(RsNamedElementIndex.KEY, RsNamedElement::class.java)
