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

import static com.google.common.truth.Truth.assertThat;

import com.google.errorprone.VisitorState;
import com.google.errorprone.scanner.Scanner;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MemberSelectTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class IsMethodReferenceOrLambdaHasReturnStatementTest extends CompilerBasedAbstractTest {
  private final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void positive() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "import java.util.stream.Collectors;",
        "import java.util.List;",
        "public class A {",
        "  public List<String> foo() {",
        "    return ImmutableList.of(1, 2).stream().map(String::valueOf).collect(Collectors.toList());",
        "  }",
        "}");

    assertCompiles(
        isFunctionMethodReference(
            /* shouldMatch= */ true, new IsMethodReferenceOrLambdaHasReturnStatement()));
  }

  @Test
  public void hasReturnStatement() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "import java.util.stream.Collectors;",
        "import java.util.List;",
        "public class A {",
        "  public List<String> bar() {",
        "    return ImmutableList.of(1, 2).stream().map(e -> { if (true) { return \"1\"; } else { return String.valueOf(e); } } ).collect(Collectors.toList());",
        "  }",
        "}");

    assertCompiles(
        isFunctionMethodReference(
            /* shouldMatch= */ true, new IsMethodReferenceOrLambdaHasReturnStatement()));
  }

  @Test
  public void negative() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "import java.util.stream.Collectors;",
        "import java.util.List;",
        "public class A {",
        "  public List<String> bar() {",
        "    return ImmutableList.of(1, 2).stream().map(e -> String.valueOf(e)).collect(Collectors.toList());",
        "  }",
        "}");

    assertCompiles(
        isFunctionMethodReference(
            /* shouldMatch= */ false, new IsMethodReferenceOrLambdaHasReturnStatement()));
  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner isFunctionMethodReference(
      final boolean shouldMatch, final Matcher<ExpressionTree> toMatch) {
    ScannerTest test =
        new ScannerTest() {
          private boolean matched = false;

          @Override
          public Void visitMemberReference(MemberReferenceTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }
            return super.visitMemberReference(node, visitorState);
          }

          @Override
          public Void visitLambdaExpression(LambdaExpressionTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }
            return super.visitLambdaExpression(node, visitorState);
          }

          @Override
          public Void visitMemberSelect(MemberSelectTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }
            return super.visitMemberSelect(node, visitorState);
          }

          @Override
          public void assertDone() {
            assertThat(shouldMatch).isEqualTo(matched);
          }
        };
    tests.add(test);
    return test;
  }
}
