package org.rust.ide.navigation.goto

import com.intellij.ide.util.gotoByName.DefaultClassNavigationContributor
import org.rust.lang.core.psi.RustItem
import org.rust.lang.core.stubs.index.RustStructOrEnumIndex

class RustClassNavigationContributor
    : RustNavigationContributorBase<RustItem>(RustStructOrEnumIndex.KEY, RustItem::class.java)

