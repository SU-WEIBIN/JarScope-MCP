package io.github.javacodeintel.mcp.query;

import io.github.javacodeintel.mcp.domain.*;
import io.github.javacodeintel.mcp.ports.IndexStore;
import java.io.IOException;
import java.util.*;

public final class FieldQueryService {
    private final IndexStore store;
    public FieldQueryService(IndexStore store){this.store=Objects.requireNonNull(store);}
    public PageResult<FieldResult> find(String owner,String fieldName,boolean includeAccessors,PageRequest request)throws IOException{List<FieldInfo> fields=store.findFields(owner,fieldName);List<FieldResult> results=new ArrayList<>();for(FieldInfo field:fields)results.add(new FieldResult(field,includeAccessors?infer(field,store.findMethods(owner,null,null)):Map.of()));int start=Math.min(request.offset(),results.size());int end=Math.min(results.size(),start+request.effectiveLimit());return PageResult.of(results.subList(start,end),request,results.size());}
    private static Map<String,String> infer(FieldInfo field,List<MethodInfo> methods){Map<String,String> result=new LinkedHashMap<>();String suffix=Character.toUpperCase(field.name().charAt(0))+field.name().substring(1);for(MethodInfo method:methods){if(method.name().equals("get"+suffix)&&method.parameters().isEmpty()&&field.type().equals(method.returnType()))result.put("getter",method.name()+method.descriptor());if(method.name().equals("is"+suffix)&&method.parameters().isEmpty()&&"boolean".equals(field.type())&&"boolean".equals(method.returnType()))result.put("getter",method.name()+method.descriptor());if(method.name().equals("set"+suffix)&&method.parameters().size()==1&&field.type().equals(method.parameters().get(0))&&"void".equals(method.returnType()))result.put("setter",method.name()+method.descriptor());}return result;}
    private static String descriptor(String type){return switch(type){case "void"->"V";case "boolean"->"Z";case "byte"->"B";case "char"->"C";case "short"->"S";case "int"->"I";case "float"->"F";case "long"->"J";case "double"->"D";default->"L"+type.replace('.','/')+";";};}
}
