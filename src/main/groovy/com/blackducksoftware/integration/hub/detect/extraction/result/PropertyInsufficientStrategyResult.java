package com.blackducksoftware.integration.hub.detect.extraction.result;

public class PropertyInsufficientStrategyResult extends FailedStrategyResult {

    @Override
    public String toDescription() {
        return "The properties are insufficient to run.";
    }
}
