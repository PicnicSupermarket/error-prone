/*
 * Copyright 2013 The Error Prone Authors.
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
import static com.google.errorprone.matchers.Matchers.methodReferenceHasParameters;

import com.google.common.collect.ImmutableList;
import com.google.errorprone.VisitorState;
import com.google.errorprone.scanner.Scanner;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DoesMethodSignatureMatchTest extends CompilerBasedAbstractTest {
  private final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void sameTypeMethodReferenceMatches() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(this::bar);",
        "  }",
        "",
        "  private Integer bar(Integer i) {",
        "    return null;",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ true,
            methodReferenceHasParameters(ImmutableList.of("java.lang.Integer"))));
  }

  @Test
  public void wrongTypeDoesntMatchMethodReference() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(Integer::valueOf);",
        "  }",
        "",
        "  private Integer bar(Integer i) {",
        "    return null;",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ false,
            methodReferenceHasParameters(ImmutableList.of("java.lang.String"))));
  }

  @Test
  public void primitiveTypeMethodReferenceMatches() {
    writeFile(
            "A.java",
            "import com.google.common.collect.ImmutableList;",
            "",
            "class A {",
            "  public void foo() {",
            "    ImmutableList.of(1).stream().map(Integer::valueOf);",
            "  }",
            "}");

    assertCompiles(
            methodSignatureIsMatching(
                    /* shouldMatch= */ true,
                    methodReferenceHasParameters(ImmutableList.of("int"))));
  }

  @Test
  public void wrapperTypeMethodReferenceMatches() {
    writeFile(
            "A.java",
            "import com.google.common.collect.ImmutableList;",
            "",
            "class A {",
            "  public void foo() {",
            "    ImmutableList.of(1).stream().map(this::bar);",
            "  }",
            "",
            "  private Integer bar(int i) {",
            "    return null;",
            "  }",
            "}");

    assertCompiles(
            methodSignatureIsMatching(
                    /* shouldMatch= */ true,
                    methodReferenceHasParameters(ImmutableList.of("java.lang.Integer"))));
  }

  @Test
  public void subtypeMethodReferenceMatches() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(Integer::valueOf);",
        "  }",
        "",
        "  private Integer bar(Integer i) {",
        "    return null;",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ true,
            methodReferenceHasParameters(ImmutableList.of("java.lang.Integer"))));
  }

  // define the order, make two tests for that. multiple arguments.
  // string to object.
  @Test
  public void typeLambdaMatches() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(i -> i * 2);",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ true,
            methodReferenceHasParameters(ImmutableList.of("java.lang.Integer"))));
  }

  @Test
  public void typeLambdaDoesntMatch() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(i -> i * 2);",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ false,
            methodReferenceHasParameters(ImmutableList.of("java.lang.String"))));
  }

  @Test
  public void subtypeLambdaMatch() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableList;",
        "",
        "class A {",
        "  public void foo() {",
        "    ImmutableList.of(1).stream().map(i -> i * 2);",
        "  }",
        "}");

    assertCompiles(
        methodSignatureIsMatching(
            /* shouldMatch= */ true,
            methodReferenceHasParameters(ImmutableList.of("java.lang.Object"))));
  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner methodSignatureIsMatching(
      final boolean shouldMatch, final Matcher<ExpressionTree> toMatch) {
    ScannerTest test =
        new ScannerTest() {
          private boolean matched = false;

          @Override
          public Void visitLambdaExpression(LambdaExpressionTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }
            return super.visitLambdaExpression(node, visitorState);
          }

          @Override
          public Void visitMemberReference(MemberReferenceTree node, VisitorState visitorState) {
            visitorState = visitorState.withPath(getCurrentPath());
            if (toMatch.matches(node, visitorState)) {
              matched = true;
            }
            return super.visitMemberReference(node, visitorState);
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
