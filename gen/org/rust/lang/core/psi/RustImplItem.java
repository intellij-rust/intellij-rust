// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustImplItem extends RustCompositeElement {

  @NotNull
  List<RustAbi> getAbiList();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @NotNull
  List<RustAnonParams> getAnonParamsList();

  @NotNull
  List<RustBounds> getBoundsList();

  @Nullable
  RustConstItem getConstItem();

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  List<RustFnParams> getFnParamsList();

  @NotNull
  List<RustGenericArgs> getGenericArgsList();

  @NotNull
  List<RustGenericParams> getGenericParamsList();

  @Nullable
  RustImplMethod getImplMethod();

  @NotNull
  List<RustInnerAttr> getInnerAttrList();

  @NotNull
  List<RustLifetimes> getLifetimesList();

  @NotNull
  List<RustOuterAttr> getOuterAttrList();

  @NotNull
  List<RustRetType> getRetTypeList();

  @NotNull
  List<RustTraitRef> getTraitRefList();

  @NotNull
  List<RustTypeParamBounds> getTypeParamBoundsList();

  @Nullable
  RustTypePrimSum getTypePrimSum();

  @Nullable
  RustWhereClause getWhereClause();

  @Nullable
  PsiElement getEq();

  @Nullable
  PsiElement getExcl();

  @NotNull
  PsiElement getImpl();

  @NotNull
  PsiElement getLbrace();

  @Nullable
  PsiElement getPub();

  @NotNull
  PsiElement getRbrace();

  @Nullable
  PsiElement getType();

}
