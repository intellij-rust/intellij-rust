// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;

public interface RustTraitRef extends RustCompositeElement {

  @NotNull
  List<RustGenericArgs> getGenericArgsList();

  @NotNull
  List<RustRetType> getRetTypeList();

  @NotNull
  List<RustTypeSums> getTypeSumsList();

  @Nullable
  PsiElement getCself();

}
