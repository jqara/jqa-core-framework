package com.buschmais.jqassistant.core.report.model;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.buschmais.jqassistant.core.report.api.SourceProvider;
import com.buschmais.jqassistant.core.report.api.model.Language;
import com.buschmais.jqassistant.core.report.api.model.LanguageElement;
import com.buschmais.xo.api.CompositeObject;

/**
 * A test language to be verified in the XML report.
 */
@Language()
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface TestLanguage {

    TestLanguageElement value();

    enum TestLanguageElement implements LanguageElement {
        TestElement,
        DerivedTestElement;

        @Override
        public SourceProvider<? extends CompositeObject> getSourceProvider() {
            return new SourceProvider<TestDescriptorWithLanguageElement>() {
                @Override
                public String getName(TestDescriptorWithLanguageElement descriptor) {
                    return descriptor.getValue();
                }

                @Override
                public String getSourceFile(TestDescriptorWithLanguageElement descriptor) {
                    return "Test.java";
                }

                @Override
                public Integer getLineNumber(TestDescriptorWithLanguageElement descriptor) {
                    return 1;
                }
            };
        }

        @Override
        public String getLanguage() {
            return "TestLanguage";
        }
    }
}
