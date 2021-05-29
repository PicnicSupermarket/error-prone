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
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Type;

import java.util.List;

import static com.google.common.collect.ImmutableList.toImmutableList;

// XXX: Naming; DoParamsMatch? MethodParamsMatcher?
public class DoesMethodSignatureMatch implements Matcher<ExpressionTree> {
  private final ImmutableList<Supplier<Type>> parameterTypes;

  public DoesMethodSignatureMatch(ImmutableList<String> parameterTypes) {
    this.parameterTypes =
        parameterTypes.stream().map(Suppliers::typeFromString).collect(toImmutableList());
  }

  @Override
  public boolean matches(ExpressionTree expressionTree, VisitorState state) {
    if (expressionTree instanceof LambdaExpressionTree) {
      ImmutableList<Type> params =
          ((LambdaExpressionTree) expressionTree)
              .getParameters().stream()
                  .map(param -> ASTHelpers.getSymbol(param).type)
                  .collect(toImmutableList());
      return hasMatchingParameters(params, state);
    }

    Symbol symbol = ASTHelpers.getSymbol(expressionTree);
    if (symbol instanceof MethodSymbol) {
      ImmutableList<Type> params =
          ((MethodSymbol) symbol)
              .getParameters().stream().map(p -> p.type).collect(toImmutableList());
      return hasMatchingParameters(params, state);
    }

    return false;
  }

  private boolean hasMatchingParameters(List<Type> params, VisitorState state) {
    if (params.size() != parameterTypes.size()) {
      return false;
    }

    for (int i = 0; i < params.size(); i++) {
      if (!state.getTypes().isConvertible(params.get(i), parameterTypes.get(i).get(state))) {
        return false;
      }
    }

    return true;
  }
}
