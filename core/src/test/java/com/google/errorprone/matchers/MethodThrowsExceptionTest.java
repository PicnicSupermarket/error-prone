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
import static com.google.errorprone.matchers.Matchers.throwsException;

import com.google.common.collect.ImmutableSet;
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
public class MethodThrowsExceptionTest extends CompilerBasedAbstractTest {
  private final List<ScannerTest> tests = new ArrayList<>();

  @After
  public void tearDown() {
    for (ScannerTest test : tests) {
      test.assertDone();
    }
  }

  @Test
  public void noThrowDoesntMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        "  @FunctionalInterface",
        "  interface Foo<I, O> {",
        "    O fun(I input) throws Exception;",
        "  }",
        "",
        "  void invoke() {",
        "    receiver(this::fun1);",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun1(Object o) {",
        "    return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ false, throwsException("java.lang.Exception")));
  }

  @Test
  public void throwDoesMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        "  @FunctionalInterface",
        "  interface Foo<I, O> {",
        "     O fun(I input) throws Exception;",
        "  }",
        "",
        "  void invoke() {",
        "     receiver(this::fun2);",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun2(Object o) throws Exception {",
        "     return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ true, throwsException("java.lang.Exception")));
  }

  @Test
  public void throwSubtypeShouldMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        "  @FunctionalInterface",
        "  interface Foo<I, O> {",
        "     O fun(I input) throws Exception;",
        "  }",
        "",
        "  void invoke() {",
        "     receiver(this::fun2);",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "  ",
        "  private String fun2(Object o) throws Exception {",
        "     return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(
            /* shouldMatch= */ true, throwsException("java.lang.IllegalStateException")));
  }

  @Test
  public void throwSupertypeShouldNotMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        "  @FunctionalInterface",
        "  interface Foo<I, O> {",
        "     O fun(I input) throws Exception;",
        "  }",
        "",
        "  void invoke() {",
        "     receiver(this::fun2);",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "  ",
        "  private String fun2(Object o) throws Exception {",
        "     return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ false, throwsException("java.lang.Throwable")));
  }

  @Test
  public void constructorThrowShouldMatch() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableSet;",
        "",
        "abstract class A {",
        "  public void Foo() {",
        "    ImmutableSet.of(1).stream().map(Test::new);",
        "  }",
        "}",
        "",
        "class Test {",
        "  public Test(int i) throws IllegalStateException { }",
        "}");

    assertCompiles(
        methodThrowsException(
            /* shouldMatch= */ true, throwsException("java.lang.IllegalStateException")));
  }

  @Test
  public void constructorDoesntThrowShouldNotMatch() {
    writeFile(
        "A.java",
        "import com.google.common.collect.ImmutableSet;",
        "",
        "abstract class A {",
        "  public void Foo() {",
        "    ImmutableSet.of(1).stream().map(Test::new);",
        "  }",
        "}",
        "",
        "class Test {",
        "  public Test(int i) { }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ false, throwsException("java.lang.Exception")));
  }

  @Test
  public void lambdaThrowsShouldMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        " @FunctionalInterface",
        " interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        " }",
        "",
        "  void invoke() {",
        "    receiver(e -> fun1(e));",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun1(Object o) throws Exception {",
        "    return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ true, throwsException("java.lang.Exception")));
  }

  @Test
  public void lambdaDoesNotThrowShouldNotMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        " @FunctionalInterface",
        " interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        " }",
        "",
        "  void invoke() {",
        "    receiver(e -> fun1(e));",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun1(Object o) {",
        "    return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ false, throwsException("java.lang.Exception")));
  }

  @Test
  public void lambdaLastStatementDoesThrowShouldMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        " @FunctionalInterface",
        " interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        " }",
        "",
        "  void invoke() {",
        "  receiver(e -> {",
        "    System.out.println(\"Test\");",
        "    return fun1(e);",
        "   });",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun1(Object o) throws Exception {",
        "    return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ true, throwsException("java.lang.Exception")));
  }

  @Test
  public void lambdaSecondStatementDoesThrowShouldMatch() {
    writeFile(
        "A.java",
        "abstract class A {",
        " @FunctionalInterface",
        " interface Foo<I, O> {",
        "   O fun(I input) throws Exception;",
        " }",
        "",
        "  void invoke() {",
        "    receiver(e -> {",
        "      System.out.println(\"Print\");",
        "      String x = fun1(e);",
        "      System.out.println(\"Print\");",
        "      return x;",
        "    });",
        "  }",
        "",
        "  abstract <I, O> void receiver(Foo<I, O> fun);",
        "",
        "  private String fun1(Object o) throws Exception {",
        "    return o.toString();",
        "  }",
        "}");

    assertCompiles(
        methodThrowsException(/* shouldMatch= */ true, throwsException("java.lang.Exception")));
  }

  private abstract static class ScannerTest extends Scanner {
    abstract void assertDone();
  }

  private Scanner methodThrowsException(
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
