// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustStmtItem extends RustCompositeElement {

  @Nullable
  RustConstItem getConstItem();

  @Nullable
  RustEnumItem getEnumItem();

  @Nullable
  RustExternCrateDecl getExternCrateDecl();

  @Nullable
  RustExternFnItem getExternFnItem();

  @Nullable
  RustFnItem getFnItem();

  @Nullable
  RustForeignModItem getForeignModItem();

  @Nullable
  RustImplItem getImplItem();

  @Nullable
  RustModItem getModItem();

  @Nullable
  RustStaticItem getStaticItem();

  @Nullable
  RustStructItem getStructItem();

  @Nullable
  RustTraitItem getTraitItem();

  @Nullable
  RustTypeItem getTypeItem();

  @Nullable
  RustUseItem getUseItem();

  @Nullable
  PsiElement getUnsafe();

}
