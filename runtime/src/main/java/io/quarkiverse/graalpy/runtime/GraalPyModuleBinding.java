package io.quarkiverse.graalpy.runtime;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GraalPyModuleBinding { String value(); }
