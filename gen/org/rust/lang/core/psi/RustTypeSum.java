// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTypeSum extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @Nullable
  RustBounds getBounds();

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  RustGenericParams getGenericParams();

  @Nullable
  RustLifetimes getLifetimes();

  @Nullable
  RustPathWithoutColons getPathWithoutColons();

  @Nullable
  RustQualPathNoTypes getQualPathNoTypes();

  @Nullable
  RustRetType getRetType();

  @Nullable
  RustTraitRef getTraitRef();

  @NotNull
  List<RustTypeSums> getTypeSumsList();

  @Nullable
  PsiElement getDotdotdot();

  @Nullable
  PsiElement getExtern();

  @Nullable
  PsiElement getFn();

  @Nullable
  PsiElement getFor();

  @Nullable
  PsiElement getGt();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getPlus();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

  @Nullable
  PsiElement getUnsafe();

}
