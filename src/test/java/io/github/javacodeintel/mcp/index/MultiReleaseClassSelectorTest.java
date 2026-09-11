package io.github.javacodeintel.mcp.index;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MultiReleaseClassSelectorTest {
    @Test
    void selectsHighestVersionNotAboveTargetAndBaseForJava8() {
        List<String> entries = List.of("demo/Order.class", "META-INF/versions/9/demo/Order.class", "META-INF/versions/11/demo/Order.class", "META-INF/versions/17/demo/Order.class");
        assertThat(MultiReleaseClassSelector.select(entries, 8)).containsExactly("demo/Order.class");
        assertThat(MultiReleaseClassSelector.select(entries, 11)).containsExactly("META-INF/versions/11/demo/Order.class");
        assertThat(MultiReleaseClassSelector.select(entries, 16)).containsExactly("META-INF/versions/11/demo/Order.class");
    }
}
