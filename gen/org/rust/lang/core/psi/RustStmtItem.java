// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustStmtItem extends RustCompositeElement {

  @Nullable
  RustBlockItem getBlockItem();

  @Nullable
  RustConstItem getConstItem();

  @Nullable
  RustExternCrateDecl getExternCrateDecl();

  @Nullable
  RustExternFnItem getExternFnItem();

  @Nullable
  RustStaticItem getStaticItem();

  @Nullable
  RustTypeItem getTypeItem();

  @Nullable
  RustUseItem getUseItem();

}
