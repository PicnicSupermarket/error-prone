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
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

import java.util.function.Predicate;

// XXX: Method name. Maybe `ThrowsException`?
public class MethodThrowsException implements Matcher<ExpressionTree> {
  private final String qualifierException;

  public MethodThrowsException(String qualifierException) {
    this.qualifierException = qualifierException;
  }

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    // XXX: @Stephan
    // Type qualifiedType = state.getTypeFromString(qualifierException); is nu dubbel, maar
    // dat is een dure operatie zei je, dus wellicht nice?
    if (expressionTree instanceof MemberReferenceTree) {
      MethodSymbol symbol = ASTHelpers.getSymbol((MemberReferenceTree) expressionTree);
      Type qualifiedExceptionType = state.getTypeFromString(qualifierException);
      return symbol != null
          && symbol.getThrownTypes().stream()
              .anyMatch(isSubtypeOfThrownException(state, qualifiedExceptionType));
    } else if (expressionTree instanceof LambdaExpressionTree) {
      Type qualifiedExceptionType = state.getTypeFromString(qualifierException);
      return ASTHelpers.getThrownExceptions(
              ((LambdaExpressionTree) expressionTree).getBody(), state)
          .stream()
          .anyMatch(isSubtypeOfThrownException(state, qualifiedExceptionType));
    }
    return false;
  }

  private Predicate<Type> isSubtypeOfThrownException(VisitorState state, Type qualifiedExceptionType) {
    return type -> ASTHelpers.isSubtype(qualifiedExceptionType, type, state);
  }
}
