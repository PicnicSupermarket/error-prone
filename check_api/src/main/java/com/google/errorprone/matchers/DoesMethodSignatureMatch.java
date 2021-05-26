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
import com.google.common.collect.Streams;
import com.google.errorprone.VisitorState;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;

import java.util.stream.Stream;

// XXX: Naming; DoParamsMatch? MethodParamsMatcher?
public class DoesMethodSignatureMatch implements Matcher<MethodTree> {

  private ImmutableList<VariableTree> variables;

  public DoesMethodSignatureMatch(ImmutableList<VariableTree> variables) {
    this.variables = variables;
  }

  @Override
  public boolean matches(MethodTree methodTree, VisitorState state) {
    Stream<Boolean> zip = Streams.zip(variables.stream(), methodTree.getParameters().stream(), (a, b) -> {
      return a.getType().equals(b.getType());
    });
    return false;
  }
}
