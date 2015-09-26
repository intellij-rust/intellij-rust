// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForeignFnItem extends RustCompositeElement {

  @NotNull
  RustGenericParams getGenericParams();

  @NotNull
  List<RustParam> getParamList();

  @Nullable
  RustRetType getRetType();

  @Nullable
  RustWhereClause getWhereClause();

  @Nullable
  PsiElement getDotdotdot();

  @NotNull
  PsiElement getFn();

  @NotNull
  PsiElement getIdentifier();

  @NotNull
  PsiElement getLparen();

  @NotNull
  PsiElement getRparen();

  @NotNull
  PsiElement getSemicolon();

}
