package org.rust.ide.navigation.goto

import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.stubs.index.RustItemIndex

class RustSymbolNavigationContributor
    : RustNavigationContributorBase<RustItem>(RustItemIndex.KEY, RustItem::class.java)
