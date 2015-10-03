// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustAnonParam extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @Nullable
  RustExpr getExpr();

  @NotNull
  List<RustGenericArgs> getGenericArgsList();

  @Nullable
  RustGenericParams getGenericParams();

  @Nullable
  RustLifetimes getLifetimes();

  @NotNull
  List<RustRetType> getRetTypeList();

  @Nullable
  RustTraitRef getTraitRef();

  @Nullable
  RustTypeSum getTypeSum();

  @NotNull
  List<RustTypeSums> getTypeSumsList();

  @Nullable
  PsiElement getAs();

  @Nullable
  PsiElement getBox();

  @Nullable
  PsiElement getColon();

  @Nullable
  PsiElement getComma();

  @Nullable
  PsiElement getConst();

  @Nullable
  PsiElement getDotdot();

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
  PsiElement getLbrack();

  @Nullable
  PsiElement getLifetime();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getMul();

  @Nullable
  PsiElement getRbrack();

  @Nullable
  PsiElement getSemicolon();

  @Nullable
  PsiElement getStaticLifetime();

  @Nullable
  PsiElement getSuper();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnsafe();

}
