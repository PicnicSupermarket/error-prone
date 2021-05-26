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
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol;

// XXX: Method name. Maybe `ThrowsException`?
public class MethodThrowsException implements Matcher<ExpressionTree> {
  private final String throwsException;

  public MethodThrowsException(String throwsException) {
    this.throwsException = throwsException;
  }

  // XXX: Subtype support
  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    // Code for the lambda expression case, as well as for a separate "does this expression throw an exception?" check
//    return ASTHelpers.getThrownExceptions(expressionTree, state).stream()
//            .anyMatch(type -> type.tsym.toString().equals(throwsException));

    // XXX: Also constructors. But can we get rid of this constraint.
    // XXX: Also lambda expression bodies
    if (!(expressionTree instanceof MemberReferenceTree)) {
      return false;
    }

    Symbol.MethodSymbol symbol = ASTHelpers.getSymbol((MemberReferenceTree) expressionTree);
    if (symbol == null) {
      return false;
    }
    return symbol.getThrownTypes().stream()
        .anyMatch(type -> type.tsym.toString().equals(throwsException));
  }
}
