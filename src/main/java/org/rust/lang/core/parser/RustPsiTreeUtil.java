package org.rust.lang.core.parser;

import com.intellij.psi.PsiElement;
import com.intellij.psi.StubBasedPsiElement;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Temporary copy of PsiTreeUtil methods to provide backward compatibility with older IDEs.
 * See `pseTreeUtilClass` in `rust.bnf`.
 */
public class RustPsiTreeUtil extends PsiTreeUtil {
    @Nullable
    public static <T extends PsiElement> T getStubChildOfType(@Nullable PsiElement element, @NotNull Class<T> aClass) {
        if (element == null) return null;
        StubElement<?> stub = element instanceof StubBasedPsiElement ? ((StubBasedPsiElement) element).getStub() : null;
        if (stub == null) {
            return getChildOfType(element, aClass);
        }
        for (StubElement childStub : stub.getChildrenStubs()) {
            PsiElement child = childStub.getPsi();
            if (aClass.isInstance(child)) {
                //noinspection unchecked
                return (T) child;
            }
        }
        return null;
    }

    @NotNull
    public static <T extends PsiElement> List<T> getStubChildrenOfTypeAsList(@Nullable PsiElement element, @NotNull Class<T> aClass) {
        if (element == null) return Collections.emptyList();

        StubElement<?> stub = null;

        if (element instanceof PsiFileImpl)
            stub = ((PsiFileImpl) element).getStub();
        else if (element instanceof StubBasedPsiElement)
            stub = ((StubBasedPsiElement) element).getStub();

        if (stub == null) {
            return getChildrenOfTypeAsList(element, aClass);
        }

        List<T> result = new SmartList<T>();
        for (StubElement childStub : stub.getChildrenStubs()) {
            PsiElement child = childStub.getPsi();
            if (aClass.isInstance(child)) {
                //noinspection unchecked
                result.add((T) child);
            }
        }
        return result;
    }
}
