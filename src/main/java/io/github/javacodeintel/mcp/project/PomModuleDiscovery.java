package io.github.javacodeintel.mcp.project;

import org.w3c.dom.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class PomModuleDiscovery {
    public List<Path> discover(Path pom) throws IOException {
        Path rootPom = pom.toAbsolutePath().normalize(); Path root = rootPom.getParent();
        Document document;
        try {
            DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true); factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true); factory.setFeature("http://xml.org/sax/features/external-general-entities",false); factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,""); document=factory.newDocumentBuilder().parse(rootPom.toFile());
        } catch (Exception e) { throw new IOException("Unable to parse Maven POM " + rootPom,e); }
        List<Path> result=new ArrayList<>(); NodeList modules=document.getElementsByTagNameNS("*","module"); for(int i=0;i<modules.getLength();i++){String value=modules.item(i).getTextContent().trim();if(value.isEmpty())continue;Path module=root.resolve(value).normalize();if(!module.startsWith(root))throw new IOException("Maven module escapes project root: "+value);Path modulePom=Files.isDirectory(module)?module.resolve("pom.xml"):module;if(Files.exists(modulePom))result.add(modulePom.toAbsolutePath().normalize());}
        return List.copyOf(result);
    }
}
