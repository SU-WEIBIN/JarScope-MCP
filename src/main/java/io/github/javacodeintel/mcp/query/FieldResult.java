package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.FieldInfo;
import java.util.Map;

public record FieldResult(FieldInfo field,Map<String,String> accessors) { public FieldResult { accessors=accessors==null?Map.of():Map.copyOf(accessors); } }
