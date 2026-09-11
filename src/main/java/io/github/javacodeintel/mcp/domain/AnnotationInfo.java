package io.github.javacodeintel.mcp.domain;
import java.util.Collections; import java.util.LinkedHashMap; import java.util.Map; import java.util.Objects;
public record AnnotationInfo(String type, Map<String,Object> values) { public AnnotationInfo { Objects.requireNonNull(type,"type"); values=values==null?Map.of():Collections.unmodifiableMap(new LinkedHashMap<>(values)); } }
