package io.github.javacodeintel.mcp.project;

import io.github.javacodeintel.mcp.domain.MavenCoordinate;
import io.github.javacodeintel.mcp.domain.MavenDependency;
import io.github.javacodeintel.mcp.domain.MavenModuleModel;
import io.github.javacodeintel.mcp.domain.MavenReactorModel;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/** Creates a temporary Maven model containing only external dependencies. */
final class MavenExternalProbePom {
    private static final String MAVEN_NS = "http://maven.apache.org/POM/4.0.0";

    private MavenExternalProbePom() {
    }

    static String render(MavenModuleModel module, MavenReactorModel reactor, boolean includeTests)
            throws IOException {
        Document source = parse(module.effectiveProjectXml());
        Element sourceProject = source.getDocumentElement();
        Document target = newDocument();
        Element project = target.createElementNS(MAVEN_NS, "project");
        project.setAttributeNS(XMLConstants.XMLNS_ATTRIBUTE_NS_URI, "xmlns", MAVEN_NS);
        target.appendChild(project);

        appendText(target, project, "modelVersion", "4.0.0");
        appendText(target, project, "groupId", "io.github.javacodeintel.jci.probe");
        appendText(target, project, "artifactId", "probe-" + module.coordinate().artifactId());
        appendText(target, project, "version", "1");
        appendText(target, project, "packaging", "pom");

        copyChild(sourceProject, project, target, "dependencyManagement");
        copyChild(sourceProject, project, target, "repositories");

        Element sourceDependencies = child(sourceProject, "dependencies");
        if (sourceDependencies != null) {
            Element dependencies = target.createElementNS(MAVEN_NS, "dependencies");
            for (Element dependency : children(sourceDependencies, "dependency")) {
                MavenDependency modelDependency = dependency(dependency);
                if (reactor.isWorkspaceDependency(modelDependency)) {
                    continue;
                }
                if (!includeTests && "test".equals(modelDependency.scope())) {
                    continue;
                }
                dependencies.appendChild(target.importNode(dependency, true));
            }
            if (dependencies.hasChildNodes()) {
                project.appendChild(dependencies);
            }
        }
        return serialize(target);
    }

    private static MavenDependency dependency(Element element) {
        return new MavenDependency(
                new MavenCoordinate(
                        text(element, "groupId"),
                        text(element, "artifactId"),
                        text(element, "version"),
                        blankToNull(text(element, "classifier"))),
                defaultValue(text(element, "type"), "jar"),
                defaultValue(text(element, "scope"), "compile"),
                Boolean.parseBoolean(text(element, "optional")));
    }

    private static Document parse(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (Exception e) {
            throw new IOException("Unable to parse effective Maven project", e);
        }
    }

    private static Document newDocument() throws IOException {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (Exception e) {
            throw new IOException("Unable to create Maven probe document", e);
        }
    }

    private static void copyChild(Element source, Element target, Document document, String name) {
        Element child = child(source, name);
        if (child != null) {
            target.appendChild(document.importNode(child, true));
        }
    }

    private static void appendText(Document document, Element parent, String name, String value) {
        Element child = document.createElementNS(MAVEN_NS, name);
        child.setTextContent(value);
        parent.appendChild(child);
    }

    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Element child : children(parent, name)) {
            return child;
        }
        return null;
    }

    private static List<Element> children(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && name.equals(localName(element))) {
                result.add(element);
            }
        }
        return result;
    }

    private static String text(Element parent, String name) {
        Element child = child(parent, name);
        return child == null ? null : blankToNull(child.getTextContent());
    }

    private static String localName(Element element) {
        return element.getLocalName() == null ? element.getNodeName() : element.getLocalName();
    }

    private static String defaultValue(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String serialize(Document document) throws IOException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IOException("Unable to serialize Maven probe POM", e);
        }
    }
}
