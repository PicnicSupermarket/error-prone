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
    ImmutableListMultimap<String, CodeTransformer> stringCodeTransformerImmutableListMultimap =
        MIGRATION_TRANSFORMER.get();

    try (FileInputStream is = new FileInputStream("src/main/java/com/google/errorprone/bugpatterns/FirstMigrationTemplate.refaster");
         // use correct path, now it is not in this dir. perhaps absolute from a root? k
         ObjectInputStream ois = new ObjectInputStream(is)) {
      CodeTransformer codeTransformer = (CodeTransformer) ois.readObject();
      ImmutableClassToInstanceMap<Annotation> annotations = codeTransformer.annotations();
    } catch (IOException | ClassNotFoundException e) {
      e.printStackTrace();
    }
    
    int size = stringCodeTransformerImmutableListMultimap.size();
  }

  private static final String REFASTER_TEMPLATE_SUFFIX = ".refaster";
  private static final String INCLUDED_TEMPLATES_PATTERN_FLAG = "Refaster:NamePattern";

  static final Supplier<ImmutableListMultimap<String, CodeTransformer>> MIGRATION_TRANSFORMER =
      Suppliers.memoize(AddDefaultMethod::loadMigrationTransformer);

  private static ImmutableListMultimap<String, CodeTransformer> loadMigrationTransformer() {
    ImmutableListMultimap.Builder<String, CodeTransformer> transformers =
        ImmutableListMultimap.builder();

    for (ClassPath.ResourceInfo resource : getClassPathResources()) {
      getRefasterTemplateName(resource)
          .ifPresent(
              templateName ->
                  loadCodeTransformer(resource)
                      .ifPresent(transformer -> transformers.put(templateName, transformer)));
    }

    return transformers.build();
  }

  private static ImmutableSet<ClassPath.ResourceInfo> getClassPathResources() {
    try {
       return ClassPath.from(
              Objects.requireNonNullElseGet(
                  AddDefaultMethod.class.getClassLoader(),
                  () -> ClassLoader.getSystemClassLoader()))
          .getResources();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to scan classpath for resources", e);
    }
  }

  private static Optional<String> getRefasterTemplateName(ClassPath.ResourceInfo resource) {
    String resourceName = resource.getResourceName();
    if (!resourceName.contains(REFASTER_TEMPLATE_SUFFIX)) {
      return Optional.empty();
    }

    int lastPathSeparator = resourceName.lastIndexOf('/');
    int beginIndex = lastPathSeparator < 0 ? 0 : lastPathSeparator + 1;
    int endIndex = resourceName.length() - REFASTER_TEMPLATE_SUFFIX.length();
    return Optional.of(resourceName.substring(beginIndex, endIndex));
  }

  private static Optional<CodeTransformer> loadCodeTransformer(ClassPath.ResourceInfo resource) {
   try (FileInputStream is = new FileInputStream("FirstMigrationTemplate.refaster");
        // use correct path, now it is not in this dir. perhaps absolute from a root? k
    ObjectInputStream ois = new ObjectInputStream(is)) {
     CodeTransformer codeTransformer = (CodeTransformer) ois.readObject();
     ImmutableClassToInstanceMap<Annotation> annotations = codeTransformer.annotations();
   } catch (IOException | ClassNotFoundException e) {
     e.printStackTrace();
   }


    try (InputStream in = resource.url().openStream();
        ObjectInputStream ois = new ObjectInputStream(in)) {
      @SuppressWarnings("BanSerializableRead" /* Part of the Refaster API. */)
      CodeTransformer codeTransformer = (CodeTransformer) ois.readObject();
      return Optional.of(codeTransformer);
    } catch (NoSuchElementException e) {
      /* For some reason we can't load the resource. Skip it. */
      // XXX: Should we log this?
      return Optional.empty();
    } catch (IOException | ClassNotFoundException e) {
      throw new IllegalStateException("Can't load `CodeTransformer` from " + resource, e);
    }
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
