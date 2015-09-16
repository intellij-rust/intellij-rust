// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.RustCompositeElementTypes.*;
import org.rust.lang.core.psi.*;

public class RustGenericArgsImpl extends RustCompositeElementImpl implements RustGenericArgs {

  public RustGenericArgsImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitGenericArgs(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustBindings getBindings() {
    return findChildByClass(RustBindings.class);
  }

  @Override
  @Nullable
  public RustLifetimes getLifetimes() {
    return findChildByClass(RustLifetimes.class);
  }

  @Override
  @Nullable
  public RustTypeSums getTypeSums() {
    return findChildByClass(RustTypeSums.class);
  }

  @Override
  @Nullable
  public PsiElement getComma() {
    return findChildByType(COMMA);
  }

  @Override
  @NotNull
  public PsiElement getGt() {
    return findNotNullChildByType(GT);
  }

  @Override
  @NotNull
  public PsiElement getLt() {
    return findNotNullChildByType(LT);
  }

}
