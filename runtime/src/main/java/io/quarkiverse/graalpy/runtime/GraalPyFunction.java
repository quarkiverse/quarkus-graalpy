package io.quarkiverse.graalpy.runtime;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GraalPyFunction { String value(); }
