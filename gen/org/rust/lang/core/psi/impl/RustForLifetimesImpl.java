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

public class RustForLifetimesImpl extends RustCompositeElementImpl implements RustForLifetimes {

  public RustForLifetimesImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitForLifetimes(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public RustLifetimes getLifetimes() {
    return findNotNullChildByClass(RustLifetimes.class);
  }

  @Override
  @NotNull
  public PsiElement getFor() {
    return findNotNullChildByType(FOR);
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
