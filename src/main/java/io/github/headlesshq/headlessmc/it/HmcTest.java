package io.github.headlesshq.headlessmc.it;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a test class whose test methods take an {@link HmcRunner} parameter.
 *
 * @see HmcExtension
 */
@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(HmcExtension.class)
public @interface HmcTest {
}
