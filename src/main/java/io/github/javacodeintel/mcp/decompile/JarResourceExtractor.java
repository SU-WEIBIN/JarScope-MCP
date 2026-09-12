package io.github.javacodeintel.mcp.decompile;
import io.github.javacodeintel.mcp.domain.DecompiledResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.regex.*;
final class JarResourceExtractor {
 private static final Set<String> EXTENSIONS=Set.of("xml","sql","yaml","yml","properties","json","conf");
 private static final Pattern NAMESPACE=Pattern.compile("<mapper\\b[^>]*\\bnamespace\\s*=\\s*['\"]([^'\"]+)['\"]",Pattern.CASE_INSENSITIVE|Pattern.DOTALL);
 private JarResourceExtractor(){}
 static List<DecompiledResource> extract(Path jar,Path outputRoot,String jarId)throws IOException{
  List<DecompiledResource> out=new ArrayList<>(); Path root=outputRoot.resolve("resources").resolve(jarId).toAbsolutePath().normalize();
  try(JarFile file=new JarFile(jar.toFile())){Enumeration<JarEntry> es=file.entries();while(es.hasMoreElements()){JarEntry e=es.nextElement();if(e.isDirectory()||!text(e.getName())||e.getSize()>8L*1024*1024)continue;Path target=root.resolve(e.getName()).normalize();if(!target.startsWith(root))continue;Files.createDirectories(target.getParent());byte[] b;try(var in=file.getInputStream(e)){b=in.readAllBytes();}Files.write(target,b,StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);String ns=null;if(e.getName().toLowerCase(Locale.ROOT).endsWith(".xml")){Matcher m=NAMESPACE.matcher(new String(b,StandardCharsets.UTF_8));if(m.find())ns=m.group(1);}out.add(new DecompiledResource(e.getName(),target,ns==null?extension(e.getName()).toUpperCase(Locale.ROOT):"MYBATIS_MAPPER",ns));}}
  return List.copyOf(out);
 }
 private static boolean text(String n){int i=n.lastIndexOf('.');return i>=0&&EXTENSIONS.contains(n.substring(i+1).toLowerCase(Locale.ROOT));}
 private static String extension(String n){int i=n.lastIndexOf('.');return i<0?"RESOURCE":n.substring(i+1);}
}
