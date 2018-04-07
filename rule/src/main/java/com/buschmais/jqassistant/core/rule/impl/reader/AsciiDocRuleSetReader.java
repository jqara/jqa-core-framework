package com.buschmais.jqassistant.core.rule.impl.reader;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.buschmais.jqassistant.core.analysis.api.rule.*;
import com.buschmais.jqassistant.core.rule.api.reader.AggregationVerification;
import com.buschmais.jqassistant.core.rule.api.reader.RowCountVerification;
import com.buschmais.jqassistant.core.rule.api.reader.RuleConfiguration;
import com.buschmais.jqassistant.core.rule.api.reader.RuleSetReader;
import com.buschmais.jqassistant.core.rule.api.source.RuleSource;

import org.apache.commons.io.IOUtils;
import org.asciidoctor.Asciidoctor;
import org.asciidoctor.ast.AbstractBlock;
import org.asciidoctor.ast.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Arrays.asList;

/**
 * @author mh
 * @since 12.10.14
 */
public class AsciiDocRuleSetReader implements RuleSetReader {

    private static final Set<String> EXECUTABLE_RULE_TYPES = new HashSet<>(asList("concept", "constraint"));

    private static final Logger LOGGER = LoggerFactory.getLogger(AsciiDocRuleSetReader.class);

    private static final Pattern DEPENDENCY_PATTERN = Pattern.compile("(.*?)(\\((.*)\\))?");

    private static final String CONCEPT = "concept";
    private static final String CONSTRAINT = "constraint";
    private static final String GROUP = "group";

    private static final String INCLUDES_GROUPS = "includesGroups";
    private static final String INCLUDES_CONCEPTS = "includesConcepts";
    private static final String INCLUDES_CONSTRAINTS = "includesConstraints";

    private static final String SEVERITY = "severity";
    private static final String DEPENDS = "depends";
    private static final String REQUIRES_CONCEPTS = "requiresConcepts";
    private static final String REQUIRES_PARAMETERS = "requiresParameters";
    private static final String REPORT_TYPE = "reportType";
    private static final String PRIMARY_REPORT_COLUM = "primaryReportColumn";
    private static final String REPORT_PROPERTIES = "reportProperties";
    private static final String VERIFY = "verify";
    private static final String AGGREGATION = "aggregation";
    private static final String AGGREGATION_COLUMN = "aggregationColumn";
    private static final String AGGREGATION_MIN = "aggregationMin";
    private static final String AGGREGATION_MAX = "aggregationMax";
    private static final String ROW_COUNT_MIN = "rowCountMin";
    private static final String ROW_COUNT_MAX = "rowCountMax";
    private static final String TITLE = "title";
    private static final String LISTING = "listing";
    private static final String SOURCE = "source";
    private static final String LANGUAGE = "language";
    private static final String CYPHER = "cypher";
    private static final String OPTIONAL = "optional";

    private RuleConfiguration ruleConfiguration;

    /**
     * The cached rule set reader, initialized lazily.
     */
    private Asciidoctor cachedAsciidoctor = null;

    AsciiDocRuleSetReader(RuleConfiguration ruleConfiguration) {
        this.ruleConfiguration = ruleConfiguration;
    }

    @Override
    public void read(List<? extends RuleSource> sources, RuleSetBuilder ruleSetBuilder) throws RuleException {
        for (RuleSource source : sources) {
            if (source.isType(RuleSource.Type.AsciiDoc)) {
                readDocument(source, ruleSetBuilder);
            }
        }
    }

    /**
     * Reads and decodes a rule source.
     *
     * @param source
     *            The source.
     * @param builder
     *            The builder to use.
     * @throws RuleException
     *             If building fails.
     */
    private void readDocument(RuleSource source, RuleSetBuilder builder) throws RuleException {
        InputStream stream;
        try {
            stream = source.getInputStream();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read rules from '" + source.getId() + "'.", e);
        }
        Document document;
        try {
            document = getAsciidoctor().load(IOUtils.toString(stream), Collections.<String, Object> emptyMap());
        } catch (IOException e) {
            throw new RuleException("Cannot parse AsciiDoc document from " + source.getId(), e);
        }
        extractRules(source, Collections.singletonList(document), builder);
    }

    /**
     * Return an Asciidoctor instance.
     *
     * Initialization is quite expensive, therefore doing it lazy.
     *
     * @return The Asciidoctor instance.
     */
    private Asciidoctor getAsciidoctor() {
        if (cachedAsciidoctor == null) {
            LOGGER.debug("Loading Asciidoctor...");
            cachedAsciidoctor = Asciidoctor.Factory.create();
        }
        return cachedAsciidoctor;
    }

    /**
     * Find all content parts representing source code listings with a role that
     * represents a rule.
     *
     *
     * @param ruleSource
     *            The rule source.
     * @param blocks
     *            The content parts of the document.
     * @param builder
     *            The {@link RuleSetBuilder}.
     */
    private void extractRules(RuleSource ruleSource, Collection<?> blocks, RuleSetBuilder builder) throws RuleException {
        for (Object element : blocks) {
            if (element instanceof AbstractBlock) {
                AbstractBlock block = (AbstractBlock) element;
                if (EXECUTABLE_RULE_TYPES.contains(block.getRole())) {
                    extractExecutableRule(ruleSource, block, builder);
                } else if (GROUP.equals(block.getRole())) {
                    extractGroup(ruleSource, block, builder);
                }
                extractRules(ruleSource, block.getBlocks(), builder);
            } else if (element instanceof Collection<?>) {
                extractRules(ruleSource, (Collection<?>) element, builder);
            }
        }
    }

    private void extractExecutableRule(RuleSource ruleSource, AbstractBlock executableRuleBlock, RuleSetBuilder builder) throws RuleException {
        Attributes attributes = new Attributes(executableRuleBlock.getAttributes());
        String id = executableRuleBlock.id();
        if (id == null) {
            throw new RuleException("An id attribute is required for the rule '" + executableRuleBlock + "'.");
        }
        String description = attributes.getString(TITLE, "");
        if (description == null) {
            LOGGER.info("Description of rule is missing: Using empty text for description (source='{}', id='{}').", ruleSource.getId(), id);
        }
        Map<String, Boolean> required = getRequiresConcepts(ruleSource, id, attributes);
        Map<String, Parameter> parameters = getParameters(attributes.getString(REQUIRES_PARAMETERS));
        String language;
        if (SOURCE.equals(executableRuleBlock.getStyle())) {
            language = attributes.getString(LANGUAGE);
        } else {
            language = executableRuleBlock.getStyle();
        }
        String source = unescapeHtml(executableRuleBlock.getContent());
        Executable executable;
        if (CYPHER.equals(language)) {
            executable = new CypherExecutable<>(source, executableRuleBlock);
        } else {
            executable = new ScriptExecutable<>(language, source, executableRuleBlock);
        }
        Verification verification;
        if (AGGREGATION.equals(attributes.getString(VERIFY))) {
            verification = AggregationVerification.builder().column(attributes.getString(AGGREGATION_COLUMN)).min(attributes.getInt(AGGREGATION_MIN))
                    .max(attributes.getInt(AGGREGATION_MAX)).build();
        } else {
            verification = RowCountVerification.builder().min(attributes.getInt(ROW_COUNT_MIN)).max(attributes.getInt(ROW_COUNT_MAX)).build();
        }
        Report report = getReport(executableRuleBlock);
        if (CONCEPT.equals(executableRuleBlock.getRole())) {
            Severity severity = getSeverity(executableRuleBlock, ruleConfiguration.getDefaultConceptSeverity());
            Concept concept = Concept.builder().id(id).description(description).severity(severity).executable(executable)
                    .requiresConceptIds(required).parameters(parameters).verification(verification).report(report).ruleSource(ruleSource).build();
            builder.addConcept(concept);
        } else if (CONSTRAINT.equals(executableRuleBlock.getRole())) {
            Severity severity = getSeverity(executableRuleBlock, ruleConfiguration.getDefaultConstraintSeverity());
            Constraint constraint = Constraint.builder().id(id).description(description).severity(severity).executable(executable)
                    .requiresConceptIds(required).parameters(parameters).verification(verification).report(report).ruleSource(ruleSource).build();
            builder.addConstraint(constraint);
        }
    }

    /**
     * Evaluates required concepts of a rule.
     *
     * @param ruleSource
     *            The rule source.
     * @param attributes
     *            The attributes of an asciidoc rule block
     * @param id
     *            The id.
     * @return A map where the keys represent the ids of required concepts and the
     *         values if they are optional.
     * @throws RuleException
     *             If the dependencies cannot be evaluated.
     */
    private Map<String, Boolean> getRequiresConcepts(RuleSource ruleSource, String id, Attributes attributes) throws RuleException {
        Map<String, String> requiresDeclarations = getDependencyDeclarations(attributes, REQUIRES_CONCEPTS);
        Map<String, String> depends = getDependencyDeclarations(attributes, DEPENDS);
        if (!depends.isEmpty()) {
            LOGGER.info("Using 'depends' to reference required concepts is deprecated, please use 'requiresConcepts' (source='{}', id='{}').",
                    ruleSource.getId(), id);
            requiresDeclarations.putAll(depends);
        }
        Map<String, Boolean> required = new HashMap<>();
        for (Map.Entry<String, String> requiresEntry : requiresDeclarations.entrySet()) {
            String conceptId = requiresEntry.getKey();
            String dependencyAttribute = requiresEntry.getValue();
            Boolean optional = dependencyAttribute != null ? OPTIONAL.equals(dependencyAttribute.toLowerCase()) : null;
            required.put(conceptId, optional);
        }
        return required;
    }

    private void extractGroup(RuleSource ruleSource, AbstractBlock groupBlock, RuleSetBuilder ruleSetBuilder) throws RuleException {
        Attributes attributes = new Attributes(groupBlock.getAttributes());
        Map<String, Severity> constraints = getGroupElements(attributes, INCLUDES_CONSTRAINTS);
        Map<String, Severity> concepts = getGroupElements(attributes, INCLUDES_CONCEPTS);
        Map<String, Severity> groups = getGroupElements(attributes, INCLUDES_GROUPS);
        Severity severity = getSeverity(groupBlock, ruleConfiguration.getDefaultGroupSeverity());
        Group group = Group.builder().id(groupBlock.id()).description(groupBlock.getTitle()).severity(severity).ruleSource(ruleSource)
                .conceptIds(concepts).constraintIds(constraints).groupIds(groups).build();
        ruleSetBuilder.addGroup(group);
    }

    private Map<String, Severity> getGroupElements(Attributes attributes, String attributeName) throws RuleException {
        Map<String, String> dependencyDeclarations = getDependencyDeclarations(attributes, attributeName);
        Map<String, Severity> result = new HashMap<>();
        for (Map.Entry<String, String> entry : dependencyDeclarations.entrySet()) {
            String id = entry.getKey();
            String dependencyAttribute = entry.getValue();
            Severity severity = dependencyAttribute != null ? Severity.fromValue(dependencyAttribute.toLowerCase()) : null;
            result.put(id, severity);
        }
        return result;
    }

    /**
     * Get dependency declarations for an attribute from a map of attributes.
     *
     * @param attributes
     *            The map of attributes.
     * @param attributeName
     *            The name of the attribute.
     * @return A map containing the ids of the dependencies as keys and their
     *         severity (optional).
     */
    private Map<String, String> getDependencyDeclarations(Attributes attributes, String attributeName) {
        String attribute = attributes.getString(attributeName);
        Set<String> dependencies = new HashSet<>();
        if (attribute != null && !attribute.trim().isEmpty()) {
            dependencies.addAll(asList(attribute.split("\\s*,\\s*")));
        }
        Map<String, String> rules = new HashMap<>();
        for (String dependency : dependencies) {
            Matcher matcher = DEPENDENCY_PATTERN.matcher(dependency);
            if (matcher.matches()) {
                String id = matcher.group(1);
                String dependencyAttribute = matcher.group(3);
                rules.put(id, dependencyAttribute);
            }
        }
        return rules;
    }

    /**
     * Extract the optional severity of a rule.
     *
     * @param part
     *            The part representing a rule.
     * @param defaultSeverity
     *            The default severity to use if no severity is specified.
     * @return The severity.
     */
    private Severity getSeverity(AbstractBlock part, Severity defaultSeverity) throws RuleException {
        Object severity = part.getAttributes().get(SEVERITY);
        if (severity == null) {
            return defaultSeverity;
        }
        Severity value = Severity.fromValue(severity.toString().toLowerCase());
        return value != null ? value : defaultSeverity;
    }

    /**
     * Unescapes the content of a rule.
     *
     * TODO do better, or even better add a partget(Original|Raw)Content() to
     * asciidoctor
     *
     * @param content
     *            The content of a rule.
     * @return The unescaped rule
     */
    private String unescapeHtml(Object content) {
        return content != null ? content.toString().replace("&lt;", "<").replace("&gt;", ">") : "";
    }

    /**
     * Create the report part of a rule.
     *
     * @param part
     *            The content part.
     * @return The report.
     */
    private Report getReport(AbstractBlock part) {
        Object primaryReportColum = part.getAttributes().get(PRIMARY_REPORT_COLUM);
        Object reportType = part.getAttributes().get(REPORT_TYPE);
        Properties reportProperties = parseProperties(part, REPORT_PROPERTIES);
        Report.Builder reportBuilder = Report.Builder.newInstance();
        if (reportType != null) {
            reportBuilder.selectTypes(reportType.toString());
        }
        if (primaryReportColum != null) {
            reportBuilder.primaryColumn(primaryReportColum.toString());
        }
        return reportBuilder.properties(reportProperties).get();
    }

    /**
     * Parse properties from an attribute.
     *
     * @param part
     *            The content part containing the attribute.
     * @param attributeName
     *            The attribute name.
     * @return The properties.
     */
    private Properties parseProperties(AbstractBlock part, String attributeName) {
        Properties properties = new Properties();
        Object attribute = part.getAttributes().get(attributeName);
        if (attribute == null) {
            return properties;
        }
        Scanner propertiesScanner = new Scanner(attribute.toString());
        propertiesScanner.useDelimiter(";");
        while (propertiesScanner.hasNext()) {
            String next = propertiesScanner.next().trim();
            if (next.length() > 0) {
                Scanner propertyScanner = new Scanner(next);
                propertyScanner.useDelimiter("=");
                String key = propertyScanner.next().trim();
                String value = propertyScanner.next().trim();
                properties.setProperty(key, value);
            }
        }
        return properties;
    }

    private Map<String, Parameter> getParameters(Object attribute) {
        if (attribute == null) {
            return Collections.emptyMap();
        }
        Map<String, Parameter> parameters = new HashMap<>();
        String[] parameterDeclarations = ((String) attribute).split(";");
        for (String parameterDeclaration : parameterDeclarations) {
            Scanner scanner = new Scanner(parameterDeclaration);
            String typeName = scanner.next();
            String name = scanner.next();
            Parameter.Type type = Parameter.Type.valueOf(typeName.toUpperCase());
            Parameter parameter = new Parameter(name, type, null);
            parameters.put(name, parameter);
        }
        return parameters;
    }

    private static final class Attributes {

        private final Map<String, Object> attributes;

        private Attributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        private Integer getInt(String key) {
            Object value = attributes.get(key);
            if (value != null) {
                return Integer.valueOf(value.toString());
            }
            return null;
        }

        private String getString(String key) {
            return getString(key, null);
        }

        private String getString(String key, String defaultValue) {
            Object value = attributes.get(key);
            if (value != null) {
                return value.toString();
            }
            return defaultValue;
        }
    }
}
