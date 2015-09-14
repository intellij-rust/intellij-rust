// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustBinding extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @Nullable
  RustAnonParams getAnonParams();

  @Nullable
  RustBounds getBounds();

  @Nullable
  RustExpr getExpr();

  @Nullable
  RustFnParams getFnParams();

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
  PsiElement getAnd();

  @Nullable
  PsiElement getAndand();

  @Nullable
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

  @NotNull
  PsiElement getEq();

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
  PsiElement getMut();

  @Nullable
  PsiElement getOror();

  @Nullable
  PsiElement getProc();

  @Nullable
  PsiElement getRbrack();

  @Nullable
  PsiElement getSelf();

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
