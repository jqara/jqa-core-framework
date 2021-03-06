package com.buschmais.jqassistant.core.plugin.impl;

import java.util.Collection;

import com.buschmais.jqassistant.core.plugin.api.PluginConfigurationReader;
import com.buschmais.jqassistant.core.rule.api.model.RuleException;
import com.buschmais.jqassistant.core.rule.api.reader.RuleParserPlugin;
import com.buschmais.jqassistant.core.rule.impl.reader.AsciidocRuleParserPlugin;
import com.buschmais.jqassistant.core.rule.impl.reader.XmlRuleParserPlugin;
import com.buschmais.jqassistant.core.rule.impl.reader.YamlRuleParserPlugin;
import com.buschmais.jqassistant.core.rule.spi.RulePluginRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.buschmais.jqassistant.core.rule.api.reader.RuleConfiguration.DEFAULT;
import static org.assertj.core.api.Assertions.assertThat;

class RulePluginRepositoryImplTest {

    RulePluginRepository ruleParserPluginRepository;

    @BeforeEach
    void setupPluginConfigurationReader() {
        PluginConfigurationReader pluginConfigurationReader = new PluginConfigurationReaderImpl();
        ruleParserPluginRepository = new RulePluginRepositoryImpl(pluginConfigurationReader);
        ruleParserPluginRepository.initialize();
    }

    @Test
    void yamlRuleReaderIsAvailable() throws RuleException {
        Collection<RuleParserPlugin> plugins = ruleParserPluginRepository.getRuleParserPlugins(DEFAULT);

        assertThat(plugins).anySatisfy(ruleParserPlugin -> {
            assertThat(ruleParserPlugin).isExactlyInstanceOf(YamlRuleParserPlugin.class);
        });
    }

    @Test
    void xmlRuleReaderIsAvailable() throws RuleException {
        Collection<RuleParserPlugin> plugins = ruleParserPluginRepository.getRuleParserPlugins(DEFAULT);

        assertThat(plugins).anySatisfy(ruleParserPlugin -> {
            assertThat(ruleParserPlugin).isExactlyInstanceOf(XmlRuleParserPlugin.class);
        });
    }

    @Test
    void asciidoctorRuleReaderIsAvailable() throws RuleException {
        Collection<RuleParserPlugin> plugins = ruleParserPluginRepository.getRuleParserPlugins(DEFAULT);

        assertThat(plugins).anySatisfy(ruleParserPlugin -> {
            assertThat(ruleParserPlugin).isExactlyInstanceOf(AsciidocRuleParserPlugin.class);
        });
    }
}
