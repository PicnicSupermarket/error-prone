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

import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import java.util.List;
import javax.annotation.Nullable;

// XXX: Naming; DoParamsMatch? MethodParamsMatcher?
public class DoesMethodSignatureMatch implements Matcher<ExpressionTree> {
  private final ImmutableList<Matcher<VariableTree>> variables;

  public DoesMethodSignatureMatch(ImmutableList<Matcher<VariableTree>> variableMatchers) {
    this.variables = variableMatchers;
  }

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    List<? extends VariableTree> parameters = getParameters(expressionTree, state);
    return parameters != null && hasMatchingParameters(state, parameters);
  }

  @Nullable
  private List<? extends VariableTree> getParameters(
      ExpressionTree expressionTree, VisitorState state) {
    if (expressionTree instanceof LambdaExpressionTree) {
      return ((LambdaExpressionTree) expressionTree).getParameters();
    }

    Symbol symbol = ASTHelpers.getSymbol(expressionTree);
    if (symbol instanceof MethodSymbol) {
      MethodTree method = ASTHelpers.findMethod((MethodSymbol) symbol, state);
      return method.getParameters();
    }

    return null;
  }

  private boolean hasMatchingParameters(
      VisitorState state, List<? extends VariableTree> parameters) {
    for (int i = 0; i < parameters.size(); i++) {
      Matcher<VariableTree> variableTreeMatcher = variables.get(i);
      boolean matches = variableTreeMatcher.matches(parameters.get(i), state);
      if (!matches) {
        return false;
      }
    }
    return true;
  }
}
