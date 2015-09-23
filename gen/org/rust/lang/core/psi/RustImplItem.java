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
  List<RustAnonParams> getAnonParamsList();

  @NotNull
  List<RustBounds> getBoundsList();

  @NotNull
  List<RustConstItem> getConstItemList();

  @NotNull
  List<RustExpr> getExprList();

  @Nullable
  RustFnParams getFnParams();

  @NotNull
  List<RustGenericArgs> getGenericArgsList();

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

  @NotNull
  List<RustRetType> getRetTypeList();

  @NotNull
  List<RustTraitRef> getTraitRefList();

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

  @NotNull
  PsiElement getImpl();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getProc();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getSelf();

  @Nullable
  PsiElement getTypeof();

  @Nullable
  PsiElement getUnderscore();

}
