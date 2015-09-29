// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustGenericArgs extends RustCompositeElement {

  @Nullable
  RustBindings getBindings();

  @Nullable
  RustLifetimes getLifetimes();

  @Nullable
  RustTypeSums getTypeSums();

  @NotNull
  PsiElement getGt();

  @NotNull
  PsiElement getLt();

}
