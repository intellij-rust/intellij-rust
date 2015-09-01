// This is a generated file. Not intended for manual editing.
package org.rust.lang.core.psi.gen.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.rust.lang.core.psi.gen.RustElementTypes.*;
import org.rust.lang.core.psi.gen.*;

public class RustUnaryExprImpl extends RustExprImpl implements RustUnaryExpr {

  public RustUnaryExprImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof RustVisitor) ((RustVisitor)visitor).visitUnaryExpr(this);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public RustExpr getExpr() {
    return findChildByClass(RustExpr.class);
  }

}
