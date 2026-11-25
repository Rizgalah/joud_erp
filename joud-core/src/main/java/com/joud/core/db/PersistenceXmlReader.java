package com.joud.core.db;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * يقرأ وحدة استمرارية من META-INF/persistence.xml دون الاعتماد على Hibernate،
 * ليتمكن القلب من دمج كيانات الوحدات المثبتة فعلاً قبل إنشاء الـ EntityManagerFactory.
 */
public final class PersistenceXmlReader {

    private static final String PERSISTENCE_XML = "META-INF/persistence.xml";

    private PersistenceXmlReader() {
    }

    /** يبحث في كل ملفات META-INF/persistence.xml على الـ classpath عن الوحدة المطلوبة. */
    public static Optional<PersistenceUnitDefinition> readFromClasspath(ClassLoader classLoader, String unitName)
            throws IOException {
        Enumeration<URL> urls = classLoader.getResources(PERSISTENCE_XML);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            URLConnection connection = url.openConnection();
            connection.setUseCaches(false); // لا نُبقي ملف jar مقفلاً (مهم على Windows)
            try (InputStream in = connection.getInputStream()) {
                Optional<PersistenceUnitDefinition> definition = read(in, unitName);
                if (definition.isPresent()) {
                    return Optional.of(definition.get().withRootUrl(rootOf(url)));
                }
            }
        }
        return Optional.empty();
    }

    /** يقرأ وحدة باسمها من مجرى XML، أو empty إن لم توجد. */
    public static Optional<PersistenceUnitDefinition> read(InputStream xml, String unitName) {
        Document document = parse(xml);
        NodeList units = document.getElementsByTagNameNS("*", "persistence-unit");
        for (int i = 0; i < units.getLength(); i++) {
            Element unit = (Element) units.item(i);
            if (unitName.equals(unit.getAttribute("name"))) {
                return Optional.of(toDefinition(unit));
            }
        }
        return Optional.empty();
    }

    private static PersistenceUnitDefinition toDefinition(Element unit) {
        String transactionType = unit.hasAttribute("transaction-type")
                ? unit.getAttribute("transaction-type").trim()
                : "RESOURCE_LOCAL";

        // exclude-unlisted-classes: العنصر بلا نص يعني true، وغيابه يعني false
        boolean exclude = false;
        List<String> excludeValues = childTexts(unit, "exclude-unlisted-classes");
        if (!excludeValues.isEmpty()) {
            String v = excludeValues.get(0);
            exclude = v.isEmpty() || Boolean.parseBoolean(v);
        }

        return new PersistenceUnitDefinition(
                unit.getAttribute("name"),
                transactionType,
                first(childTexts(unit, "provider")),
                first(childTexts(unit, "jta-data-source")),
                first(childTexts(unit, "non-jta-data-source")),
                new ArrayList<>(new LinkedHashSet<>(childTexts(unit, "class"))),
                childTexts(unit, "mapping-file"),
                exclude,
                properties(unit),
                null);
    }

    private static Map<String, String> properties(Element unit) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Element propertiesElement : children(unit, "properties")) {
            for (Element property : children(propertiesElement, "property")) {
                String name = property.getAttribute("name");
                if (!name.isEmpty()) {
                    result.put(name, property.getAttribute("value"));
                }
            }
        }
        return result;
    }

    // ─── DOM helpers ────────────────────────────────

    private static List<Element> children(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && localName.equals(n.getLocalName())) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private static List<String> childTexts(Element parent, String localName) {
        List<String> result = new ArrayList<>();
        for (Element e : children(parent, localName)) {
            result.add(e.getTextContent().trim());
        }
        return result;
    }

    private static String first(List<String> values) {
        return values.isEmpty() || values.get(0).isEmpty() ? null : values.get(0);
    }

    private static Document parse(InputStream xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(xml);
        } catch (Exception e) {
            throw new IllegalArgumentException("persistence.xml is not valid: " + e.getMessage(), e);
        }
    }

    private static URL rootOf(URL persistenceXmlUrl) {
        String external = persistenceXmlUrl.toExternalForm();
        if (!external.endsWith(PERSISTENCE_XML)) {
            return null;
        }
        try {
            return java.net.URI.create(external.substring(0, external.length() - PERSISTENCE_XML.length())).toURL();
        } catch (Exception e) {
            return null;
        }
    }
}
