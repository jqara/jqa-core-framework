package com.buschmais.jqassistant.core.analysis.api;

import java.util.Map;

import org.slf4j.Logger;

import com.buschmais.jqassistant.core.analysis.api.rule.Verification;
import com.buschmais.jqassistant.core.analysis.impl.VerificationStrategy;
import com.buschmais.jqassistant.core.store.api.Store;

public interface AnalyzerContext {

    Store getStore();

    Logger getLogger();

    Map<Class<? extends Verification>, VerificationStrategy> getVerificationStrategies();

}