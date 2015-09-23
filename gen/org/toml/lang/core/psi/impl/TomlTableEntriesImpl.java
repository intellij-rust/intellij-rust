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

public class TomlTableEntriesImpl extends ASTWrapperPsiElement implements TomlTableEntries {

  public TomlTableEntriesImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull TomlVisitor visitor) {
    visitor.visitTableEntries(this);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof TomlVisitor) accept((TomlVisitor)visitor);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public List<TomlKeyValue> getKeyValueList() {
    return PsiTreeUtil.getChildrenOfTypeAsList(this, TomlKeyValue.class);
  }

}
