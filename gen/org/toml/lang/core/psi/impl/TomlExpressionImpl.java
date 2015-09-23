// This is a generated file. Not intended for manual editing.
package org.toml.lang.core.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static org.toml.lang.core.psi.TomlTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import org.toml.lang.core.psi.*;

public class TomlExpressionImpl extends ASTWrapperPsiElement implements TomlExpression {

  public TomlExpressionImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull TomlVisitor visitor) {
    visitor.visitExpression(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof TomlVisitor) accept((TomlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @Nullable
  public TomlKeyValue getKeyValue() {
    return findChildByClass(TomlKeyValue.class);
  }

  @Override
  @Nullable
  public TomlTable getTable() {
    return findChildByClass(TomlTable.class);
  }

}
