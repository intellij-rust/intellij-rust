// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustWherePred extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @Nullable
  RustBounds getBounds();

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  RustForLifetimes getForLifetimes();

  @NotNull
  List<RustGenericArgs> getGenericArgsList();

  @Nullable
  RustGenericParams getGenericParams();

  @Nullable
  RustLifetimes getLifetimes();

  @NotNull
  List<RustRetType> getRetTypeList();

  @NotNull
  List<RustTraitRef> getTraitRefList();

  @Nullable
  RustTypeParamBounds getTypeParamBounds();

  @NotNull
  List<RustTypeSum> getTypeSumList();

  @NotNull
  List<RustTypeSums> getTypeSumsList();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getDotdotdot();

  @Nullable
  PsiElement getExtern();

  @Nullable
  PsiElement getFn();

  @Nullable
  PsiElement getFor();

  @Nullable
  PsiElement getSuper();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

  @Nullable
  PsiElement getUnsafe();

}
