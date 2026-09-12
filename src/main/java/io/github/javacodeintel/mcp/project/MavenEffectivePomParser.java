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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Parses Maven Help Plugin effective-POM output into a workspace model. */
public final class MavenEffectivePomParser {
    public MavenReactorModel parse(String xml, Path rootPom, List<Path> modulePoms) throws IOException {
        Path normalizedRootPom = rootPom.toAbsolutePath().normalize();
        List<Path> knownPoms = new ArrayList<>();
        knownPoms.add(normalizedRootPom);
        if (modulePoms != null) {
            modulePoms.stream().map(path -> path.toAbsolutePath().normalize()).forEach(knownPoms::add);
        }

        List<Element> projects = projectElements(parseXml(xml).getDocumentElement());
        if (projects.isEmpty()) {
            throw new IOException("Maven effective POM contains no projects");
        }

        List<MavenModuleModel> modules = new ArrayList<>();
        Set<Path> usedPoms = new LinkedHashSet<>();
        Path workspaceRoot = normalizedRootPom.getParent();
        for (int i = 0; i < projects.size(); i++) {
            Element project = projects.get(i);
            addInferredPom(project, workspaceRoot, knownPoms);
            Path pom = matchPom(project, knownPoms, usedPoms, i);
            usedPoms.add(pom);
            Path base = pom.getParent();
            Element build = directChild(project, "build");
            MavenCoordinate coordinate = new MavenCoordinate(
                    text(project, "groupId"),
                    text(project, "artifactId"),
                    text(project, "version"),
                    null);
            String packaging = defaultValue(text(project, "packaging"), "jar");
            Path source = resolve(base, text(build, "sourceDirectory"), "src/main/java");
            Path testSource = resolve(base, text(build, "testSourceDirectory"), "src/test/java");
            Path output = resolve(base, text(build, "outputDirectory"), "target/classes");
            Path testOutput = resolve(base, text(build, "testOutputDirectory"), "target/test-classes");
            modules.add(new MavenModuleModel(
                    pom,
                    base,
                    coordinate,
                    packaging,
                    source,
                    testSource,
                    output,
                    testOutput,
                    targetRelease(project),
                    dependencies(project),
                    serialize(project)));
        }
        return new MavenReactorModel(normalizedRootPom, modules);
    }

    /**
     * Effective POM output can contain active-profile modules that are absent
     * from the raw module list. Infer their POM from Maven's absolute model
     * directories before falling back to the raw discovery order.
     */
    private static void addInferredPom(Element project, Path workspaceRoot, List<Path> knownPoms) {
        String[] modelDirectories = {
                text(project, "basedir"),
                text(directChild(project, "build"), "directory"),
                text(directChild(project, "build"), "sourceDirectory")};
        for (String value : modelDirectories) {
            Path directory = absolutePath(value);
            Path inferred = findNearestPom(directory, workspaceRoot);
            if (inferred != null && !knownPoms.contains(inferred)) {
                knownPoms.add(inferred);
                return;
            }
        }
    }

    private static Path findNearestPom(Path path, Path workspaceRoot) {
        if (path == null || workspaceRoot == null || !path.startsWith(workspaceRoot)) {
            return null;
        }
        Path cursor = Files.isDirectory(path) ? path : path.getParent();
        while (cursor != null && cursor.startsWith(workspaceRoot)) {
            Path candidate = cursor.resolve("pom.xml");
            if (java.nio.file.Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
            if (cursor.equals(workspaceRoot)) {
                break;
            }
            cursor = cursor.getParent();
        }
        return null;
    }

    private static List<MavenDependency> dependencies(Element project) {
        Element dependencies = directChild(project, "dependencies");
        if (dependencies == null) {
            return List.of();
        }
        List<MavenDependency> result = new ArrayList<>();
        for (Element dependency : directChildren(dependencies, "dependency")) {
            result.add(new MavenDependency(
                    new MavenCoordinate(
                            text(dependency, "groupId"),
                            text(dependency, "artifactId"),
                            text(dependency, "version"),
                            blankToNull(text(dependency, "classifier"))),
                    defaultValue(text(dependency, "type"), "jar"),
                    defaultValue(text(dependency, "scope"), "compile"),
                    Boolean.parseBoolean(text(dependency, "optional"))));
        }
        return List.copyOf(result);
    }

    private static int targetRelease(Element project) {
        Element properties = directChild(project, "properties");
        for (String property : List.of("maven.compiler.release", "maven.compiler.target", "java.version")) {
            String value = text(properties, property);
            Integer parsed = parseRelease(value);
            if (parsed != null) {
                return parsed;
            }
        }
        Element build = directChild(project, "build");
        Element plugins = directChild(build, "plugins");
        if (plugins != null) {
            for (Element plugin : directChildren(plugins, "plugin")) {
                if (!"maven-compiler-plugin".equals(text(plugin, "artifactId"))) {
                    continue;
                }
                Element configuration = directChild(plugin, "configuration");
                for (String option : List.of("release", "target", "source")) {
                    Integer parsed = parseRelease(text(configuration, option));
                    if (parsed != null) {
                        return parsed;
                    }
                }
            }
        }
        return 8;
    }

    private static Integer parseRelease(String value) {
        if (value == null || value.isBlank() || value.contains("${")) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.startsWith("1.")) {
            normalized = normalized.substring(2);
        }
        int end = 0;
        while (end < normalized.length() && Character.isDigit(normalized.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return null;
        }
        try {
            return Integer.parseInt(normalized.substring(0, end));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Path matchPom(Element project, List<Path> knownPoms, Set<Path> used, int index)
            throws IOException {
        String buildDirectory = text(directChild(project, "build"), "directory");
        String sourceDirectory = text(directChild(project, "build"), "sourceDirectory");
        Path buildPath = absolutePath(buildDirectory);
        Path sourcePath = absolutePath(sourceDirectory);
        Path best = null;
        int bestLength = -1;
        for (Path pom : knownPoms) {
            if (used.contains(pom)) {
                continue;
            }
            Path base = pom.getParent();
            boolean matches = buildPath != null && buildPath.startsWith(base)
                    || sourcePath != null && sourcePath.startsWith(base);
            if (matches && base.getNameCount() > bestLength) {
                best = pom;
                bestLength = base.getNameCount();
            }
        }
        if (best != null) {
            return best;
        }
        if (index < knownPoms.size() && !used.contains(knownPoms.get(index))) {
            return knownPoms.get(index);
        }
        throw new IOException("Unable to map effective Maven project "
                + text(project, "artifactId") + " to a workspace POM");
    }

    private static Path absolutePath(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            Path path = Path.of(value);
            return path.isAbsolute() ? path.toAbsolutePath().normalize() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path resolve(Path base, String value, String fallback) {
        String selected = value == null || value.isBlank() ? fallback : value.trim();
        try {
            Path path = Path.of(selected);
            return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            // Some effective POMs contain malformed network paths such as
            // //host-without-share. Ignore that entry and retain Maven's
            // conventional directory relative to the module.
            return base.resolve(fallback).toAbsolutePath().normalize();
        }
    }

    private static List<Element> projectElements(Element root) {
        if ("project".equals(localName(root))) {
            return List.of(root);
        }
        return directChildren(root, "project");
    }

    private static Document parseXml(String xml) throws IOException {
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
            throw new IOException("Unable to parse Maven effective POM", e);
        }
    }

    private static String serialize(Element element) throws IOException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            var transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(element), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IOException("Unable to serialize effective Maven project", e);
        }
    }

    private static Element directChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Element child : directChildren(parent, name)) {
            return child;
        }
        return null;
    }

    private static List<Element> directChildren(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node instanceof Element element && name.equals(localName(element))) {
                result.add(element);
            }
        }
        return result;
    }

    private static String text(Element parent, String name) {
        Element child = directChild(parent, name);
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
}
