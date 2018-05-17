package com.blackducksoftware.integration.hub.detect.extraction.bomtool.gradle.parse;

import com.blackducksoftware.integration.hub.detect.model.DetectCodeLocation;

public class GradleParseResult {
    public String projectName;
    public String projectVersion;
    public DetectCodeLocation codeLocation;

    public GradleParseResult(final String projectName, final String projectVersion, final DetectCodeLocation codeLocation) {
        this.projectName = projectName;
        this.projectVersion = projectVersion;
        this.codeLocation = codeLocation;
    }
}
