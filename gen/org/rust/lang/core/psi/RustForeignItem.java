// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForeignItem extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  RustForeignFnItem getForeignFnItem();

  @Nullable
  RustGenericParams getGenericParams();

  @Nullable
  RustLifetimes getLifetimes();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

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
  PsiElement getGt();

  @Nullable
  PsiElement getIdentifier();

  @Nullable
  PsiElement getLt();

  @Nullable
  PsiElement getPub();

  @Nullable
  PsiElement getStatic();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

  @Nullable
  PsiElement getUnsafe();

}
