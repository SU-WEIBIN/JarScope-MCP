package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.domain.AnnotationInfo;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Opcodes;

import java.util.*;

class AnnotationCollector extends AnnotationVisitor {
    private final String type;
    private final Map<String, Object> values = new LinkedHashMap<>();

    AnnotationCollector(String descriptor) {
        super(Opcodes.ASM9);
        this.type = descriptorToName(descriptor);
    }

    @Override public void visit(String name, Object value) { values.put(name == null ? "value" : name, value); }
    @Override public AnnotationVisitor visitAnnotation(String name, String descriptor) { Nested nested = new Nested(descriptor); values.put(name == null ? "value" : name, nested); return nested; }
    @Override public AnnotationVisitor visitArray(String name) { ArrayValue array = new ArrayValue(); values.put(name == null ? "value" : name, array.values); return array; }
    AnnotationInfo result() { return new AnnotationInfo(type, values); }

    private static String descriptorToName(String descriptor) { return descriptor.substring(1, descriptor.length() - 1).replace('/', '.'); }

    private static final class Nested extends AnnotationVisitor {
        private final String type; private final Map<String,Object> values = new LinkedHashMap<>();
        Nested(String descriptor) { super(Opcodes.ASM9); this.type=descriptorToName(descriptor); }
        @Override public void visit(String name,Object value){values.put(name==null?"value":name,value);}
        @Override public AnnotationVisitor visitArray(String name){ArrayValue array=new ArrayValue();values.put(name==null?"value":name,array.values);return array;}
        @Override public void visitEnd(){values.putIfAbsent("@type",type);}
    }
    private static final class ArrayValue extends AnnotationVisitor { final List<Object> values=new ArrayList<>(); ArrayValue(){super(Opcodes.ASM9);} @Override public void visit(String name,Object value){values.add(value);} }
}
