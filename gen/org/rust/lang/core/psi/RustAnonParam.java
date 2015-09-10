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
  RustAnonParams getAnonParams();

  @NotNull
  List<RustBindings> getBindingsList();

  @Nullable
  RustBounds getBounds();

  @Nullable
  RustExpr getExpr();

  @Nullable
  RustFnParams getFnParams();

  @Nullable
  RustGenericParams getGenericParams();

  @NotNull
  List<RustLifetimes> getLifetimesList();

  @NotNull
  List<RustRetType> getRetTypeList();

  @Nullable
  RustTraitRef getTraitRef();

  @NotNull
  List<RustTypeParamBounds> getTypeParamBoundsList();

  @Nullable
  PsiElement getBox();

  @Nullable
  PsiElement getColon();

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
  PsiElement getLbrack();

  @Nullable
  PsiElement getLifetime();

  @Nullable
  PsiElement getMul();

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
  PsiElement getUnsafe();

}
