package md.utm.faf.pad.player.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a controller method to require "Authorization: Bearer &lt;jwt&gt;".
 * {@link AuthInterceptor} checks it before the method runs.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresAuth {
}
