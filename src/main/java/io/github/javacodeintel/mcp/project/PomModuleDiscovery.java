package io.github.javacodeintel.mcp.project;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PomModuleDiscovery {
    public List<Path> discover(Path pom) throws IOException {
        Path rootPom = pom.toAbsolutePath().normalize();
        if (!Files.isRegularFile(rootPom)) {
            throw new IOException("Maven POM does not exist: " + rootPom);
        }
        Path projectRoot = rootPom.getParent();
        List<Path> result = new ArrayList<>();
        Set<Path> visited = new LinkedHashSet<>();
        visited.add(rootPom);
        discoverChildren(rootPom, projectRoot, visited, result);
        return List.copyOf(result);
    }

    private void discoverChildren(Path pom, Path projectRoot, Set<Path> visited, List<Path> result)
            throws IOException {
        Element project = parse(pom).getDocumentElement();
        Element modules = directChild(project, "modules");
        if (modules == null) {
            return;
        }
        NodeList children = modules.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element moduleElement) || !"module".equals(moduleElement.getLocalName())) {
                continue;
            }
            String value = moduleElement.getTextContent().trim();
            if (value.isEmpty()) {
                continue;
            }
            Path modulePath = pom.getParent().resolve(value).normalize();
            Path modulePom = resolvePom(modulePath).toAbsolutePath().normalize();
            if (!modulePom.startsWith(projectRoot)) {
                throw new IOException("Maven module escapes project root: " + value);
            }
            if (!Files.isRegularFile(modulePom)) {
                throw new IOException("Maven module POM does not exist: " + modulePom);
            }
            if (visited.add(modulePom)) {
                result.add(modulePom);
                discoverChildren(modulePom, projectRoot, visited, result);
            }
        }
    }

    private static Path resolvePom(Path modulePath) {
        if (Files.isDirectory(modulePath)) {
            return modulePath.resolve("pom.xml");
        }
        if (Files.isRegularFile(modulePath)) {
            return modulePath;
        }
        Path fileName = modulePath.getFileName();
        if (fileName != null && fileName.toString().endsWith(".xml")) {
            return modulePath;
        }
        return modulePath.resolve("pom.xml");
    }

    private static Document parse(Path pom) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(pom.toFile());
        } catch (Exception e) {
            throw new IOException("Unable to parse Maven POM " + pom, e);
        }
    }

    private static Element directChild(Element parent, String localName) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && localName.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }
}
