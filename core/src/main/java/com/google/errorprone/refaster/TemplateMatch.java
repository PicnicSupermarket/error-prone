/*
 * Copyright 2013 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.refaster;

import static com.google.common.base.Preconditions.checkNotNull;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Context;
import java.io.IOException;

/**
 * Abstract type representing a match against a {@code Template}.
 *
 * @author lowasser@google.com (Louis Wasserman)
 */
public abstract class TemplateMatch {
  // This should not be here. `Inliner` should define a similar constant, but with a more generic
  // type parameter (perhaps just `Tree`).
  public static final Context.Key<JCTree> MATCH_LOCATION = new Context.Key<>();

  private final JCTree location;
  private final Unifier unifier;

  public TemplateMatch(JCTree location, Unifier unifier) {
    this.location = checkNotNull(location);
    this.unifier = checkNotNull(unifier);
  }

  public JCTree getLocation() {
    return location;
  }

  public Unifier getUnifier() {
    return unifier;
  }

  public Inliner createInliner() {
    // This should not be here; pass `location` to the `Inliner` creation instead.
    Inliner inliner = unifier.createInliner();
    inliner.getContext().put(MATCH_LOCATION, location);
    return inliner;
  }

  public String getRange(JCCompilationUnit unit) {
    try {
      CharSequence sequence = unit.getSourceFile().getCharContent(true);
      return sequence
          .subSequence(location.getStartPosition(), location.getEndPosition(unit.endPositions))
          .toString();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
