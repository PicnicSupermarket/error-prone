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
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.List;

public class MethodThrowsException implements Matcher<MemberReferenceTree> {

  private final Matcher<? super Tree> throwsMatcher;

  public MethodThrowsException(Matcher<? super Tree> throwsMatcher) {
    this.throwsMatcher = throwsMatcher;
  }

  @Override
  public boolean matches(MemberReferenceTree methodTree, VisitorState state) {
    List<Type> thrownTypes = ASTHelpers.getSymbol(methodTree).getThrownTypes();
    boolean matches = throwsMatcher.matches(methodTree, state);
    int size = thrownTypes.size();

    return size == 1;
  }
}
