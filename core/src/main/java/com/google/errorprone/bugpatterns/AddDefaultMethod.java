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

package com.google.errorprone.bugpatterns;

import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableClassToInstanceMap;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.reflect.ClassPath;
import com.google.errorprone.BugPattern;
import com.google.errorprone.CodeTransformer;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.LiteralTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.tree.TreeMaker;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.UncheckedIOException;
import java.lang.annotation.Annotation;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static com.google.errorprone.BugPattern.SeverityLevel.ERROR;

/** A {@link BugChecker}; see the associated {@link BugPattern} annotation for details. */
@BugPattern(
    name = "AddDefaultMethod",
    summary = "First steps in trying to add a default method in an interface",
    severity = ERROR)
public class AddDefaultMethod extends BugChecker
    implements MethodInvocationTreeMatcher, LiteralTreeMatcher {

  public AddDefaultMethod() {
    ImmutableListMultimap<String, CodeTransformer> migrationTransformationsMap =
        MIGRATION_TRANSFORMER.get();

    int size = migrationTransformationsMap.size();
  }

  private static final String REFASTER_TEMPLATE_SUFFIX = ".refaster";

  static final Supplier<ImmutableListMultimap<String, CodeTransformer>> MIGRATION_TRANSFORMER =
      Suppliers.memoize(AddDefaultMethod::loadMigrationTransformer);

  private static ImmutableListMultimap<String, CodeTransformer> loadMigrationTransformer() {
    ImmutableListMultimap.Builder<String, CodeTransformer> transformers =
        ImmutableListMultimap.builder();

    String refasterUri =
        "src/main/java/com/google/errorprone/bugpatterns/FirstMigrationTemplate.refaster";
    try (FileInputStream is = new FileInputStream(refasterUri);
        ObjectInputStream ois = new ObjectInputStream(is)) {
      String name = getRefasterTemplateName(refasterUri).orElseThrow(IllegalStateException::new);
      CodeTransformer codeTransformer = (CodeTransformer) ois.readObject();
      transformers.put(name, codeTransformer);
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }

    return transformers.build();
  }

  private static Optional<String> getRefasterTemplateName(String resourceName) {
    int lastPathSeparator = resourceName.lastIndexOf('/');
    int beginIndex = lastPathSeparator < 0 ? 0 : lastPathSeparator + 1;
    int endIndex = resourceName.length() - REFASTER_TEMPLATE_SUFFIX.length();
    return Optional.of(resourceName.substring(beginIndex, endIndex));
  }

  @Override
  public Description matchMethodInvocation(MethodInvocationTree tree, VisitorState state) {
    // Here try to get a match when you create a TreeLiteral of "2".
    TreeMaker treeMaker = state.getTreeMaker();
    return Description.NO_MATCH;
  }

  @Override
  public Description matchLiteral(LiteralTree tree, VisitorState state) {
    return Description.NO_MATCH;
  }
}
