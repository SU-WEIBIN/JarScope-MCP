package io.github.javacodeintel.mcp.index;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.BytecodeParser;
import org.objectweb.asm.*;

import java.util.*;

/** Parses class files without loading or initializing analyzed classes. */
public final class AsmBytecodeParser implements BytecodeParser {
    @Override public ParsedClass parse(byte[] bytecode, JarArtifact source) {
        Objects.requireNonNull(bytecode, "bytecode");
        Holder holder = new Holder(source);
        new ClassReader(bytecode).accept(holder, ClassReader.SKIP_FRAMES);
        return holder.result();
    }

    private static final class Holder extends ClassVisitor {
        private final JarArtifact jar; private String name, internal, packageName, kind, superName, signature, sourceFile; private int access, major;
        private final List<String> interfaces = new ArrayList<>(); private final List<AnnotationInfo> annotations = new ArrayList<>();
        private final List<FieldInfo> fields = new ArrayList<>(); private final List<MethodInfo> methods = new ArrayList<>(); private final List<CallEdge> calls = new ArrayList<>();
        Holder(JarArtifact jar){super(Opcodes.ASM9);this.jar=jar;}
        @Override public void visit(int version,int access,String name,String signature,String superName,String[] interfaces){
            this.major=version & 0xFFFF; this.access=access; this.internal=name; this.name=name.replace('/','.'); int dot=this.name.lastIndexOf('.'); this.packageName=dot<0?"":this.name.substring(0,dot); this.signature=signature; this.superName=superName==null?null:superName.replace('/','.'); if((access&Opcodes.ACC_ANNOTATION)!=0)kind="annotation"; else if((access&Opcodes.ACC_INTERFACE)!=0)kind="interface"; else if((access&Opcodes.ACC_ENUM)!=0)kind="enum"; else kind="class"; if(interfaces!=null)for(String value:interfaces)this.interfaces.add(value.replace('/','.'));
        }
        @Override public void visitSource(String source,String debug){this.sourceFile=source;}
        @Override public AnnotationVisitor visitAnnotation(String descriptor,boolean visible){AnnotationCollector collector=new AnnotationCollector(descriptor){@Override public void visitEnd(){annotations.add(result());}};return collector;}
        @Override public FieldVisitor visitField(int access,String name,String descriptor,String signature,Object value){
            List<AnnotationInfo> anns=new ArrayList<>(); return new FieldVisitor(Opcodes.ASM9){@Override public AnnotationVisitor visitAnnotation(String d,boolean v){AnnotationCollector c=new AnnotationCollector(d){@Override public void visitEnd(){anns.add(result());}};return c;} @Override public void visitEnd(){fields.add(new FieldInfo(Holder.this.name,name,typeName(descriptor),signature,access,(access&Opcodes.ACC_STATIC)!=0,(access&Opcodes.ACC_FINAL)!=0,anns));}};
        }
        @Override public MethodVisitor visitMethod(int access,String name,String descriptor,String signature,String[] exceptions){
            Type methodType=Type.getMethodType(descriptor); List<String> params=new ArrayList<>(); for(Type type:methodType.getArgumentTypes())params.add(typeName(type.getDescriptor())); List<String> ex=exceptions==null?List.of():Arrays.stream(exceptions).map(v->v.replace('/','.')).toList(); List<AnnotationInfo> anns=new ArrayList<>();
            return new MethodVisitor(Opcodes.ASM9){@Override public AnnotationVisitor visitAnnotation(String d,boolean v){AnnotationCollector c=new AnnotationCollector(d){@Override public void visitEnd(){anns.add(result());}};return c;} @Override public void visitMethodInsn(int opcode,String owner,String calledName,String calledDescriptor,boolean itf){calls.add(new CallEdge(Holder.this.name,name,descriptor,owner.replace('/','.'),calledName,calledDescriptor,opcodeName(opcode),ResolutionKind.DIRECT,jar));} @Override public void visitInvokeDynamicInsn(String calledName,String calledDescriptor,Handle bootstrapMethodHandle,Object... bootstrapMethodArguments){calls.add(new CallEdge(Holder.this.name,name,descriptor,bootstrapMethodHandle.getOwner().replace('/','.'),calledName,calledDescriptor,"invokedynamic",ResolutionKind.UNRESOLVED,jar));} @Override public void visitEnd(){methods.add(new MethodInfo(Holder.this.name,name,descriptor,typeName(methodType.getReturnType().getDescriptor()),params,ex,access,signature,anns));}};
        }
        ParsedClass result(){return new ParsedClass(new ClassInfo(name,internal,packageName,kind,access,superName,interfaces,signature,sourceFile,major,jar,annotations),fields,methods,calls);}
    }
    private static String typeName(String descriptor){Type type=Type.getType(descriptor);return switch(type.getSort()){case Type.ARRAY -> type.getClassName();case Type.OBJECT -> type.getClassName();case Type.VOID -> "void";case Type.BOOLEAN -> "boolean";case Type.BYTE -> "byte";case Type.CHAR -> "char";case Type.SHORT -> "short";case Type.INT -> "int";case Type.FLOAT -> "float";case Type.LONG -> "long";case Type.DOUBLE -> "double";default -> descriptor;};}
    private static String opcodeName(int opcode){return switch(opcode){case Opcodes.INVOKEVIRTUAL->"invokevirtual";case Opcodes.INVOKESPECIAL->"invokespecial";case Opcodes.INVOKESTATIC->"invokestatic";case Opcodes.INVOKEINTERFACE->"invokeinterface";default->"invoke"+opcode;};}
}
