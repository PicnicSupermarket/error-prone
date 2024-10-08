/*
 * Copyright 2016 The Error Prone Authors.
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

package com.google.errorprone.bugpatterns.android;

import static com.google.errorprone.BugPattern.SeverityLevel.WARNING;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * @author avenet@google.com (Arnaud J. Venet)
 */
@RunWith(JUnit4.class)
public class FragmentNotInstantiableTest {
  /** Used for testing a custom FragmentNotInstantiable. */
  @BugPattern(
      summary =
          "Subclasses of CustomFragment must be instantiable via Class#newInstance():"
              + " the class must be public, static and have a public nullary constructor",
      severity = WARNING)
  public static class CustomFragmentNotInstantiable extends FragmentNotInstantiable {
    public CustomFragmentNotInstantiable() {
      super(ImmutableSet.of("com.google.errorprone.bugpatterns.android.testdata.CustomFragment"));
    }
  }

  @Test
  public void positiveCases() {
    createCompilationTestHelper(FragmentNotInstantiable.class)
        .addSourceFile("testdata/FragmentNotInstantiablePositiveCases.java")
        .doTest();
  }

  @Test
  public void negativeCase() {
    createCompilationTestHelper(FragmentNotInstantiable.class)
        .addSourceFile("testdata/FragmentNotInstantiableNegativeCases.java")
        .doTest();
  }

  @Test
  public void positiveCases_custom() {
    createCompilationTestHelper(CustomFragmentNotInstantiable.class)
        .addSourceFile("testdata/FragmentNotInstantiablePositiveCases.java")
        .addSourceFile("testdata/CustomFragment.java")
        .addSourceFile("testdata/CustomFragmentNotInstantiablePositiveCases.java")
        .doTest();
  }

  @Test
  public void negativeCase_custom() {
    createCompilationTestHelper(CustomFragmentNotInstantiable.class)
        .addSourceFile("testdata/FragmentNotInstantiableNegativeCases.java")
        .addSourceFile("testdata/CustomFragment.java")
        .addSourceFile("testdata/CustomFragmentNotInstantiableNegativeCases.java")
        .doTest();
  }

  private CompilationTestHelper createCompilationTestHelper(
      Class<? extends FragmentNotInstantiable> bugCheckerClass) {
    return CompilationTestHelper.newInstance(bugCheckerClass, getClass())
        .addSourceFile("testdata/stubs/android/app/Fragment.java")
        .addSourceFile("testdata/stubs/android/support/v4/app/Fragment.java")
        .setArgs(ImmutableList.of("-XDandroidCompatible=true"));
  }
}
