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

package com.google.errorprone.migration;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;
import static com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import static com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;

import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Symbol;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@AutoService(BugChecker.class)
@BugPattern(
    name = "CountMethods",
    summary = "Count the callers of RxJava methods and Reactor methods.",
    severity = ERROR)
public class CountMethods extends BugChecker
    implements MethodInvocationTreeMatcher, MemberReferenceTreeMatcher {

  public static final ImmutableSet<String> RXJAVA_TYPES =
      ImmutableSet.of(
          "io.reactivex.Observable",
          "io.reactivex.Flowable",
          "io.reactivex.Single",
          "io.reactivex.Maybe",
          "io.reactivex.Completable");

  public static final ImmutableSet<String> REACTOR_TYPES =
      ImmutableSet.of("reactor.core.publisher.Mono", "reactor.core.publisher.Flux");

  @Override
  public Description matchMemberReference(MemberReferenceTree tree, VisitorState state) {
    printLinesForRxJavaAndReactorMethods(tree, "REF");

    return Description.NO_MATCH;
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    printLinesForRxJavaAndReactorMethods(tree, "INV");

    return Description.NO_MATCH;
  }

  private void printLinesForRxJavaAndReactorMethods(ExpressionTree tree, String invocation) {
    Symbol.MethodSymbol symbol = (Symbol.MethodSymbol) ASTHelpers.getSymbol(tree);
    boolean matches = RXJAVA_TYPES.contains(symbol.owner.toString());

    if (matches) {
      String line =
          "1 | "
              + symbol.getSimpleName()
              + " | "
              + symbol.owner.toString()
              + " | "
              + invocation
              + "\n";
      writeString(line, "rx-java");
    }

    boolean matchesReactor = REACTOR_TYPES.contains(symbol.owner.toString());

    if (matchesReactor) {
      String line =
          "1 | "
              + symbol.getSimpleName()
              + " | "
              + symbol.owner.toString()
              + " | "
              + invocation
              + "\n";
      writeString(line, "reactor");
    }
  }

  private void writeString(String line, String filePrefix) {
    byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
    FileOutputStream fileOutputStream;
    try {
      fileOutputStream = new FileOutputStream(filePrefix + "-usages.txt", true);
      fileOutputStream.write(bytes);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
