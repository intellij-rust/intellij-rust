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

public class RustWhileLetExprImpl extends RustExprImpl implements RustWhileLetExpr {

  public RustWhileLetExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitWhileLetExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustBlock getBlock() {
    return findChildByClass(RustBlock.class);
  }

  @Override
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
  }

  @Override
  @NotNull
  public RustPat getPat() {
    return findNotNullChildByClass(RustPat.class);
  }

  @Override
  @NotNull
  public PsiElement getEq() {
    return findNotNullChildByType(EQ);
  }

  @Override
  @Nullable
  public PsiElement getLbrace() {
    return findChildByType(LBRACE);
  }

  @Override
  @NotNull
  public PsiElement getLet() {
    return findNotNullChildByType(LET);
  }

  @Override
  @Nullable
  public PsiElement getRbrace() {
    return findChildByType(RBRACE);
  }

  @Override
  @NotNull
  public PsiElement getWhile() {
    return findNotNullChildByType(WHILE);
  }

}
