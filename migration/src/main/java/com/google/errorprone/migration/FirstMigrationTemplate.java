package com.google.errorprone.migration;

import com.google.errorprone.refaster.annotation.AfterTemplate;
import com.google.errorprone.refaster.annotation.BeforeTemplate;
import com.google.errorprone.refaster.annotation.MigrationTemplate;

import java.util.concurrent.Callable;

public final class FirstMigrationTemplate {
  private FirstMigrationTemplate() {}

  @MigrationTemplate(value = false, from = String.class, to = Integer.class)
  static final class MigrateStringToInteger<T extends Callable<Integer>, S extends String> {
    @BeforeTemplate
    S before(S s) {
      return s;
    }

    @AfterTemplate
    T after(String s) {
      return null;
    }
  }

  @MigrationTemplate(value = true, from = Integer.class, to = String.class)
  static final class MigrateIntegerToString {
    @BeforeTemplate
    Integer before(Integer s) {
      return s;
    }

    @AfterTemplate
    String after(Integer s) {
      return String.valueOf(s);
    }
  }
}
