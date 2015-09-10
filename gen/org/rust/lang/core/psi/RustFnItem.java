// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.psi.PsiElement;
import org.rust.lang.core.resolve.scope.RustResolveScope;

public interface RustFnItem extends RustResolveScope {

  @Nullable
  RustBlock getBlock();

  @Nullable
  RustFnParams getFnParams();

  @Nullable
  RustGenericParams getGenericParams();

  @Nullable
  RustRetType getRetType();

  @Nullable
  RustWhereClause getWhereClause();

  @NotNull
  PsiElement getFn();

  @NotNull
  PsiElement getIdentifier();

}
