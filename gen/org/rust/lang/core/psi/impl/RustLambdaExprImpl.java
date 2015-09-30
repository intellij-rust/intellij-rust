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

public class RustLambdaExprImpl extends RustExprImpl implements RustLambdaExpr {

  public RustLambdaExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull RustVisitor visitor) {
    visitor.visitLambdaExpr(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) accept((RustVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
  }

  @Override
  @NotNull
  public List<RustPat> getPatList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustPat.class);
  }

  @Override
  @Nullable
  public RustRetType getRetType() {
    return findChildByClass(RustRetType.class);
  }

  @Override
  @NotNull
  public List<RustTypeAscription> getTypeAscriptionList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, RustTypeAscription.class);
  }

  @Override
  @Nullable
  public PsiElement getMove() {
    return findChildByType(MOVE);
  }

  @Override
  @Nullable
  public PsiElement getOror() {
    return findChildByType(OROR);
  }

}
