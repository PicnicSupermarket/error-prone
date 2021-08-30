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

import com.google.errorprone.VisitorState;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.util.TreePath;

public class ReturnTreeMatcher implements Matcher<ExpressionTree> {

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    if (state.getPath().getParentPath().getLeaf() instanceof CompilationUnitTree
        || state.getPath().getParentPath().getParentPath().getLeaf()
            instanceof CompilationUnitTree) {
      return true;
    }

    // How handle it, when the expression is in a `thenReturn` of Mockito.
    boolean isPartOfReturnExpr = false;
    TreePath parentPath = state.getPath().getParentPath();
    while (!(parentPath.getLeaf() instanceof MethodTree)) {
      if (parentPath.getLeaf() instanceof ReturnTree) {
        isPartOfReturnExpr = true;
        break;
      }
      parentPath = parentPath.getParentPath();
    }

    if (!isPartOfReturnExpr) {
      return false;
    }

    parentPath = state.getPath().getParentPath();
    while (!(parentPath.getLeaf() instanceof MethodTree)) { // while not the method tree
      if (parentPath.getLeaf() instanceof LambdaExpressionTree) {
        return false;
      }
      parentPath = parentPath.getParentPath();
    }
    return true;
  }
}
