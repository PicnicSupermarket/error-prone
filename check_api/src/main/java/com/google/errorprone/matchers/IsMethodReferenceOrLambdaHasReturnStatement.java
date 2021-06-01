/*
 * Copyright 2022 The Error Prone Authors.
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
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.util.TreeScanner;

public class IsMethodReferenceOrLambdaHasReturnStatement implements Matcher<ExpressionTree> {

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    if (expressionTree instanceof LambdaExpressionTree) {
      HasReturnTree hasReturnTree = new HasReturnTree();
      hasReturnTree.scan(expressionTree, null);
      return hasReturnTree.hasReturnStatement();
    }

    if (!(expressionTree instanceof MemberReferenceTree)) {
      return false;
    }
    return ((MemberReferenceTree) expressionTree)
        .getMode()
        .equals(MemberReferenceTree.ReferenceMode.INVOKE);
  }

  private static final class HasReturnTree extends TreeScanner<Void, Void> {
    private boolean hasReturnStatement = false;

    public boolean hasReturnStatement() {
      return hasReturnStatement;
    }

    @Override
    public Void visitMethod(MethodTree node, Void unused) {
      return null;
    }

    @Override
    public Void visitReturn(ReturnTree tree, Void unused) {
      hasReturnStatement = true;
      return super.visitReturn(tree, unused);
    }
  }
}
