// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTypeItem extends RustCompositeElement {

  @NotNull
  RustGenericParams getGenericParams();

  @NotNull
  RustTypeSum getTypeSum();

  @Nullable
  RustWhereClause getWhereClause();

  @NotNull
  PsiElement getEq();

  @NotNull
  PsiElement getIdentifier();

  @NotNull
  PsiElement getSemicolon();

  @NotNull
  PsiElement getType();

}
