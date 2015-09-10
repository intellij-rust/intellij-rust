// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustEnumArgs extends RustCompositeElement {

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

  @NotNull
  List<RustGenericParams> getGenericParamsList();

  @NotNull
  List<RustLifetimes> getLifetimesList();

  @NotNull
  List<RustRetType> getRetTypeList();

  @NotNull
  List<RustStructDeclField> getStructDeclFieldList();

  @NotNull
  List<RustTraitRef> getTraitRefList();

  @NotNull
  List<RustTypeParamBounds> getTypeParamBoundsList();

  @Nullable
  PsiElement getEq();

  @Nullable
  PsiElement getLbrace();

  @Nullable
  PsiElement getRbrace();

}
