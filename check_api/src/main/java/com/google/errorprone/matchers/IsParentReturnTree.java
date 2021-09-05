/*
 * Copyright 2021 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.matchers;

import static com.google.errorprone.matchers.Matchers.anyOf;
import static com.google.errorprone.matchers.Matchers.instanceMethod;
import static com.google.errorprone.matchers.Matchers.staticMethod;

import com.google.errorprone.VisitorState;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.util.TreePath;

public class IsParentReturnTree implements Matcher<ExpressionTree> {

  private static final Matcher<ExpressionTree> MOCKITO_METHODS =
      anyOf(
          staticMethod().onClass("org.mockito.Mockito").named("when"),
          instanceMethod().onDescendantOf("org.mockito.stubbing.Stubber").named("when"),
          instanceMethod().onDescendantOf("org.mockito.stubbing.OngoingStubbing").withAnyName(),
          staticMethod().onClass("org.mockito.Mockito").named("verify"));

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    if (state.getPath().getParentPath().getLeaf() instanceof CompilationUnitTree
        || state.getPath().getParentPath().getParentPath().getLeaf()
            instanceof CompilationUnitTree) {
      return true;
    }

    boolean isPartOfReturnExpr = false;
    TreePath parentPath = state.getPath().getParentPath();
    while (!(parentPath.getLeaf() instanceof MethodTree)) {
      if (parentPath.getLeaf() instanceof ReturnTree) {
        isPartOfReturnExpr = true;
        break;
      } else if (parentPath.getLeaf() instanceof ExpressionTree
          && MOCKITO_METHODS.matches((ExpressionTree) parentPath.getLeaf(), state)) {
        return true;
      }
      parentPath = parentPath.getParentPath();
    }

    if (!isPartOfReturnExpr) {
      return false;
    }

    parentPath = parentPath.getParentPath();
    while (!(parentPath.getLeaf() instanceof MethodTree)) {
      if (parentPath.getLeaf() instanceof ReturnTree) {
        // There is a parent which is the more direct return.
        return false;
      }
      parentPath = parentPath.getParentPath();
    }
    // The return was the most outer return.
    return true;
  }
}
