/*
 * Copyright 2019 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.errorprone.bugpatterns;

import com.google.errorprone.CompilationTestHelper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/**
 * Tests for {@link ModifySourceCollectionInStream} bugpattern.
 *
 * @author deltazulu@google.com (Donald Duo Zhao)
 */
@RunWith(JUnit4.class)
public class ModifySourceCollectionInStreamTest {

  private final CompilationTestHelper compilationHelper =
      CompilationTestHelper.newInstance(ModifySourceCollectionInStream.class, getClass());

  @Test
  public void positiveCases() {
    compilationHelper
        .addSourceLines(
            "ModifySourceCollectionInStreamPositiveCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test cases for {@link com.google.errorprone.bugpatterns.ModifySourceCollectionInStream}.
 *
 * @author deltazulu@google.com (Donald Duo Zhao)
 */
public class ModifySourceCollectionInStreamPositiveCases {

  private final List<Integer> mutableValues = Arrays.asList(1, 2, 3);

  private void mutateStreamSourceMethodReference() {

    mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        // BUG: Diagnostic contains:
        .forEach(mutableValues::remove);

    this.mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        // BUG: Diagnostic contains:
        .forEach(mutableValues::remove);

    getMutableValues().parallelStream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        // BUG: Diagnostic contains:
        .forEach(getMutableValues()::add);

    getMutableValues().stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        // BUG: Diagnostic contains:
        .forEach(this.getMutableValues()::remove);

    ModifySourceCollectionInStreamPositiveCases[] cases = {
      new ModifySourceCollectionInStreamPositiveCases(),
      new ModifySourceCollectionInStreamPositiveCases()
    };
    cases[0].mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        // BUG: Diagnostic contains:
        .forEach(cases[0].mutableValues::add);
  }

  private List<Integer> mutateStreamSourceLambdaExpression(
      ImmutableList<Integer> mutableParamList) {
    Stream<Integer> values1 =
        mutableParamList.stream()
            .map(
                x -> {
                  // BUG: Diagnostic contains:
                  mutableParamList.add(x);
                  return x + 1;
                });

    Stream<Integer> values2 =
        mutableParamList.stream()
            .filter(
                x -> {
                  // BUG: Diagnostic contains:
                  mutableParamList.remove(x);
                  return mutableParamList.size() > 5;
                });

    return Stream.concat(values1, values2).collect(Collectors.toList());
  }

  private List<Integer> getMutableValues() {
    return mutableValues;
  }
}\
""")
        .doTest();
  }

  @Test
  public void negativeCasse() {
    compilationHelper
        .addSourceLines(
            "ModifySourceCollectionInStreamNegativeCases.java",
"""
package com.google.errorprone.bugpatterns.testdata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test cases for {@link com.google.errorprone.bugpatterns.ModifySourceCollectionInStream}.
 *
 * @author deltazulu@google.com (Donald Duo Zhao)
 */
public class ModifySourceCollectionInStreamNegativeCases {

  private final List<Integer> mutableValues = Arrays.asList(1, 2, 3);

  private void mutateStreamSourceMethodReference() {

    List<Integer> mutableValues = new ArrayList<>();
    mutableValues.stream().map(x -> x + 1).filter(x -> x < 5).forEach(this.mutableValues::add);

    mutableValues.forEach(this.mutableValues::add);

    ModifySourceCollectionInStreamNegativeCases[] cases = {
      new ModifySourceCollectionInStreamNegativeCases(),
      new ModifySourceCollectionInStreamNegativeCases()
    };

    cases[0].mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        .forEach(cases[1].mutableValues::add);
  }

  private List<Integer> mutateStreamSourceLambdaExpression() {

    List<Integer> localCopy = new ArrayList<>();

    Stream<Integer> values1 =
        mutableValues.stream()
            .map(
                x -> {
                  localCopy.add(x);
                  return x + 1;
                });

    Stream<Integer> values2 =
        mutableValues.stream()
            .filter(
                x -> {
                  localCopy.remove(x);
                  return mutableValues.size() > 5;
                });

    return Stream.concat(values1, values2).collect(Collectors.toList());
  }

  private void mutateStreamSourceInNonStreamApi() {
    mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        .findAny()
        .ifPresent(mutableValues::add);

    mutableValues.stream()
        .map(x -> x + 1)
        .filter(x -> x < 5)
        .findFirst()
        .ifPresent(value -> mutableValues.remove(value));
  }

  private void mutateDifferentStreamSource() {
    // Mutate a different stream source.
    mutableValues.stream().filter(x -> x < 5).collect(Collectors.toList()).stream()
        .forEach(mutableValues::remove);

    // Mutate source collection whose stream has been closed.
    mutableValues.stream()
        .filter(x -> x < 5)
        .collect(Collectors.toList())
        .forEach(mutableValue -> mutableValues.remove(mutableValue));
  }

  private void mutateNonCollectionStreamSource(CustomContainer<Double> vals) {
    vals.stream().map(x -> 2.0 * x).forEach(vals::add);
  }

  private void lambdaExpressionAsInitializer(List<Double> vals) {
    Consumer<Double> consumer = x -> vals.remove(x);
  }

  private interface CustomContainer<T> {
    Stream<T> stream();

    boolean add(T t);
  }
}\
""")
        .doTest();
  }
}
