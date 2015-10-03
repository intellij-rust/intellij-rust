// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustCastExpr extends RustExpr {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

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

  @Nullable
  RustTypeSums getTypeSums();

  @Nullable
  PsiElement getAnd();

  @Nullable
  PsiElement getAndand();

  @NotNull
  PsiElement getAs();

  @Nullable
  PsiElement getBox();

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
  PsiElement getLparen();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getMul();

  @Nullable
  PsiElement getMut();

  @Nullable
  PsiElement getRbrack();

  @Nullable
  PsiElement getRparen();

  @Nullable
  PsiElement getSemicolon();

  @Nullable
  PsiElement getStaticLifetime();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

  @Nullable
  PsiElement getUnsafe();

}
