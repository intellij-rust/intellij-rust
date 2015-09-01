// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.rust.lang.core.psi.RustCompositeElement;

public interface RustItem extends RustCompositeElement {

  @Nullable
  RustConstItem getConstItem();

  @Nullable
  RustEnumItem getEnumItem();

  @Nullable
  RustExternBlock getExternBlock();

  @Nullable
  RustFnItem getFnItem();

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

}
