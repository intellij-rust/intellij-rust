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

public class RustMethodCallExprImpl extends RustExprImpl implements RustMethodCallExpr {

  public RustMethodCallExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitMethodCallExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustArgList getArgList() {
    return findChildByClass(RustArgList.class);
  }

  @Override
  @NotNull
  public RustExpr getExpr() {
    return findNotNullChildByClass(RustExpr.class);
  }

  @Override
  @Nullable
  public RustGenericArgs getGenericArgs() {
    return findChildByClass(RustGenericArgs.class);
  }

  @Override
  @Nullable
  public PsiElement getColoncolon() {
    return findChildByType(COLONCOLON);
  }

  @Override
  @NotNull
  public PsiElement getDot() {
    return findNotNullChildByType(DOT);
  }

  @Override
  @Nullable
  public PsiElement getIdentifier() {
    return findChildByType(IDENTIFIER);
  }

}
