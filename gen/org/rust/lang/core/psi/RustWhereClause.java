// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustWhereClause extends RustCompositeElement {

  @NotNull
  List<RustWherePred> getWherePredList();

  @NotNull
  PsiElement getWhere();

}
