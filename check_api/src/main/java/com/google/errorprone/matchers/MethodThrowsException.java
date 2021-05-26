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

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.tree.JCTree;

// XXX: Method name. Maybe `ThrowsException`?
public class MethodThrowsException implements Matcher<ExpressionTree> {
  private final String qualifierException;

  public MethodThrowsException(String qualifierException) {
    this.qualifierException = qualifierException;
  }

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    // Code for the lambda expression case, as well as for a separate "does this expression throw an
    // exception?" check
    //    return ASTHelpers.getThrownExceptions(expressionTree, state).stream()
    //            .anyMatch(type -> type.tsym.toString().equals(throwsException));

    ImmutableCollection<Type> thrownTypes;
    if (expressionTree instanceof MemberReferenceTree) {
      MethodSymbol symbol = ASTHelpers.getSymbol((MemberReferenceTree) expressionTree);
      if (symbol == null) {
        return false;
      }
      thrownTypes = ImmutableList.copyOf(symbol.getThrownTypes());
    } else if (expressionTree instanceof LambdaExpressionTree) {
      thrownTypes =
          ASTHelpers.getThrownExceptions(((JCTree.JCLambda) expressionTree).getBody(), state);
    } else {
      return false;
    }

    return thrownTypes.stream()
        .anyMatch(
            type ->
                type.tsym.toString().equals(qualifierException)
                    || ASTHelpers.isSubtype(
                        state.getTypeFromString(qualifierException), type, state));
  }
}
