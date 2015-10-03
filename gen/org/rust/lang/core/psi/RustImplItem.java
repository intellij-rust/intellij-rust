// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustImplItem extends RustCompositeElement {

  @Nullable
  RustAbi getAbi();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @NotNull
  List<RustConstItem> getConstItemList();

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  List<RustGenericParams> getGenericParamsList();

  @NotNull
  List<RustImplMethod> getImplMethodList();

  @NotNull
  List<RustInnerAttr> getInnerAttrList();

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

  @Nullable
  RustTypePrimSum getTypePrimSum();

  @NotNull
  List<RustTypeSum> getTypeSumList();

  @NotNull
  List<RustTypeSums> getTypeSumsList();

  @Nullable
  RustWhereClause getWhereClause();

  @Nullable
  PsiElement getDotdotdot();

  @Nullable
  PsiElement getExcl();

  @Nullable
  PsiElement getExtern();

  @Nullable
  PsiElement getFn();

  @Nullable
  PsiElement getFor();

  @Nullable
  PsiElement getGt();

  @NotNull
  PsiElement getImpl();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getLt();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

}
