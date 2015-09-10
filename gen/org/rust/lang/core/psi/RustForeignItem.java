// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustForeignItem extends RustCompositeElement {

  @NotNull
  List<RustAbi> getAbiList();

  @NotNull
  List<RustAnonParam> getAnonParamList();

  @NotNull
  List<RustAnonParams> getAnonParamsList();

  @NotNull
  List<RustBindings> getBindingsList();

  @NotNull
  List<RustBounds> getBoundsList();

  @NotNull
  List<RustExpr> getExprList();

  @NotNull
  List<RustFnParams> getFnParamsList();

  @Nullable
  RustForeignFnItem getForeignFnItem();

  @NotNull
  List<RustGenericParams> getGenericParamsList();

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
  PsiElement getColon();

  @Nullable
  PsiElement getPub();

  @Nullable
  PsiElement getStatic();

}
