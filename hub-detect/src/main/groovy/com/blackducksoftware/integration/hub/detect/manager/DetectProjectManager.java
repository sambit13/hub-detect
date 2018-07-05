/**
 * hub-detect
 *
 * Copyright (C) 2018 Black Duck Software, Inc.
 * http://www.blackducksoftware.com/
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.blackducksoftware.integration.hub.detect.manager;

import java.io.File;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.hub.detect.bomtool.search.report.ExtractionSummaryReporter;
import com.blackducksoftware.integration.hub.detect.configuration.DetectConfigWrapper;
import com.blackducksoftware.integration.hub.detect.configuration.DetectProperty;
import com.blackducksoftware.integration.hub.detect.exception.DetectUserFriendlyException;
import com.blackducksoftware.integration.hub.detect.exitcode.ExitCodeReporter;
import com.blackducksoftware.integration.hub.detect.exitcode.ExitCodeType;
import com.blackducksoftware.integration.hub.detect.extraction.model.BomToolEvaluation;
import com.blackducksoftware.integration.hub.detect.manager.result.codelocation.DetectCodeLocationResult;
import com.blackducksoftware.integration.hub.detect.manager.result.extraction.ExtractionResult;
import com.blackducksoftware.integration.hub.detect.manager.result.search.SearchResult;
import com.blackducksoftware.integration.hub.detect.model.BomToolGroupType;
import com.blackducksoftware.integration.hub.detect.model.DetectCodeLocation;
import com.blackducksoftware.integration.hub.detect.model.DetectProject;
import com.blackducksoftware.integration.hub.detect.project.BomToolProjectInfo;
import com.blackducksoftware.integration.hub.detect.project.BomToolProjectInfoDecider;
import com.blackducksoftware.integration.hub.detect.summary.BomToolSummaryResult;
import com.blackducksoftware.integration.hub.detect.summary.SummaryResultReporter;
import com.blackducksoftware.integration.hub.summary.Result;
import com.blackducksoftware.integration.util.NameVersion;

public class DetectProjectManager implements SummaryResultReporter, ExitCodeReporter {
    private final Logger logger = LoggerFactory.getLogger(DetectProjectManager.class);

    private final Map<BomToolGroupType, Result> bomToolSummaryResults = new HashMap<>();

    private final SearchManager searchManager;
    private final ExtractionManager extractionManager;
    private final DetectCodeLocationManager codeLocationManager;
    private final DetectBdioManager bdioManager;
    private final ExtractionSummaryReporter extractionSummaryReporter;
    private final BomToolProjectInfoDecider bomToolProjectInfoDecider;
    private final DetectConfigWrapper detectConfigWrapper;

    public DetectProjectManager(final SearchManager searchManager, final ExtractionManager extractionManager, final DetectCodeLocationManager codeLocationManager, final DetectBdioManager bdioManager,
            final ExtractionSummaryReporter extractionSummaryReporter, final BomToolProjectInfoDecider bomToolProjectInfoDecider, final DetectConfigWrapper detectConfigWrapper) {
        this.searchManager = searchManager;
        this.extractionManager = extractionManager;
        this.codeLocationManager = codeLocationManager;
        this.bdioManager = bdioManager;
        this.extractionSummaryReporter = extractionSummaryReporter;
        this.bomToolProjectInfoDecider = bomToolProjectInfoDecider;
        this.detectConfigWrapper = detectConfigWrapper;
    }

    public DetectProject createDetectProject() throws DetectUserFriendlyException {
        final SearchResult searchResult = searchManager.performSearch();

        final ExtractionResult extractionResult = extractionManager.performExtractions(searchResult.getBomToolEvaluations());
        applyBomToolStatus(extractionResult.getSuccessfulBomToolTypes(), extractionResult.getFailedBomToolTypes());

        final NameVersion nameVersion = getProjectNameVersion(searchResult.getBomToolEvaluations());
        final String projectName = nameVersion.getName();
        final String projectVersion = nameVersion.getVersion();

        final List<DetectCodeLocation> codeLocations = extractionResult.getDetectCodeLocations();

        final List<File> bdioFiles = new ArrayList<>();
        if (StringUtils.isBlank(detectConfigWrapper.getProperty(DetectProperty.DETECT_BOM_AGGREGATE_NAME))) {
            final DetectCodeLocationResult codeLocationResult = codeLocationManager.process(codeLocations, projectName, projectVersion);
            applyFailedBomToolStatus(codeLocationResult.getFailedBomToolTypes());

            final List<File> createdBdioFiles = bdioManager.createBdioFiles(codeLocationResult.getBdioCodeLocations(), projectName, projectVersion);
            bdioFiles.addAll(createdBdioFiles);

            extractionSummaryReporter.print(searchResult.getBomToolEvaluations(), codeLocationResult.getCodeLocationNames());
        } else {
            final File aggregateBdioFile = bdioManager.createAggregateBdioFile(codeLocations, projectName, projectVersion);
            bdioFiles.add(aggregateBdioFile);
        }

        final DetectProject project = new DetectProject(projectName, projectVersion, bdioFiles);

        return project;
    }

    @Override
    public List<BomToolSummaryResult> getDetectSummaryResults() {
        final List<BomToolSummaryResult> detectSummaryResults = new ArrayList<>();
        for (final Map.Entry<BomToolGroupType, Result> entry : bomToolSummaryResults.entrySet()) {
            detectSummaryResults.add(new BomToolSummaryResult(entry.getKey(), entry.getValue()));
        }
        return detectSummaryResults;
    }

    @Override
    public ExitCodeType getExitCodeType() {
        for (final Map.Entry<BomToolGroupType, Result> entry : bomToolSummaryResults.entrySet()) {
            if (Result.FAILURE == entry.getValue()) {
                return ExitCodeType.FAILURE_BOM_TOOL;
            }
        }
        return ExitCodeType.SUCCESS;
    }

    private void applyFailedBomToolStatus(final Set<BomToolGroupType> failedBomToolTypes) {
        for (final BomToolGroupType type : failedBomToolTypes) {
            bomToolSummaryResults.put(type, Result.FAILURE);
        }
    }

    private void applyBomToolStatus(final Set<BomToolGroupType> successBomToolTypes, final Set<BomToolGroupType> failedBomToolTypes) {
        applyFailedBomToolStatus(failedBomToolTypes);
        for (final BomToolGroupType type : successBomToolTypes) {
            if (!bomToolSummaryResults.containsKey(type)) {
                bomToolSummaryResults.put(type, Result.SUCCESS);
            }
        }
    }

    private NameVersion getProjectNameVersion(final List<BomToolEvaluation> bomToolEvaluations) {
        final Optional<NameVersion> bomToolSuggestedNameVersion = findBomToolProjectNameAndVersion(bomToolEvaluations);

        String projectName = detectConfigWrapper.getProperty(DetectProperty.DETECT_PROJECT_NAME);
        if (StringUtils.isBlank(projectName) && bomToolSuggestedNameVersion.isPresent()) {
            projectName = bomToolSuggestedNameVersion.get().getName();
        }

        if (StringUtils.isBlank(projectName)) {
            logger.info("A project name could not be decided. Using the name of the source path.");
            projectName = new File(detectConfigWrapper.getProperty(DetectProperty.DETECT_SOURCE_PATH)).getName();
        }

        String projectVersionName = detectConfigWrapper.getProperty(DetectProperty.DETECT_PROJECT_VERSION_NAME);
        if (StringUtils.isBlank(projectVersionName) && bomToolSuggestedNameVersion.isPresent()) {
            projectVersionName = bomToolSuggestedNameVersion.get().getVersion();
        }

        if (StringUtils.isBlank(projectVersionName)) {
            if ("timestamp".equals(detectConfigWrapper.getProperty(DetectProperty.DETECT_DEFAULT_PROJECT_VERSION_SCHEME))) {
                logger.info("A project version name could not be decided. Using the current timestamp.");
                final String timeformat = detectConfigWrapper.getProperty(DetectProperty.DETECT_DEFAULT_PROJECT_VERSION_TIMEFORMAT);
                final String timeString = DateTimeFormatter.ofPattern(timeformat).withZone(ZoneOffset.UTC).format(Instant.now().atZone(ZoneOffset.UTC));
                projectVersionName = timeString;
            } else {
                logger.info("A project version name could not be decided. Using the default version text.");
                projectVersionName = detectConfigWrapper.getProperty(DetectProperty.DETECT_DEFAULT_PROJECT_VERSION_TEXT);
            }
        }

        return new NameVersion(projectName, projectVersionName);
    }

    private Optional<NameVersion> findBomToolProjectNameAndVersion(final List<BomToolEvaluation> bomToolEvaluations) {
        final String projectBomTool = detectConfigWrapper.getProperty(DetectProperty.DETECT_PROJECT_BOM_TOOL);
        Optional<BomToolGroupType> preferredBomToolType = Optional.empty();
        if (StringUtils.isNotBlank(projectBomTool)) {
            final String projectBomToolFixed = projectBomTool.toUpperCase();
            if (!BomToolGroupType.POSSIBLE_NAMES.contains(projectBomToolFixed)) {
                logger.info("A valid preferred bom tool type was not provided, deciding project name automatically.");
            } else {
                preferredBomToolType = Optional.of(BomToolGroupType.valueOf(projectBomToolFixed));
            }
        }

        final List<BomToolProjectInfo> allBomToolProjectInfo = createBomToolProjectInfo(bomToolEvaluations);
        return bomToolProjectInfoDecider.decideProjectInfo(allBomToolProjectInfo, preferredBomToolType);
    }

    private List<BomToolProjectInfo> createBomToolProjectInfo(final List<BomToolEvaluation> bomToolEvaluations) {
        return bomToolEvaluations.stream()
                .filter(it -> it.isExtractionSuccess())
                .filter(it -> it.extraction.projectName != null)
                .map(it -> {
                    final NameVersion nameVersion = new NameVersion(it.extraction.projectName, it.extraction.projectVersion);
                    final BomToolProjectInfo possibility = new BomToolProjectInfo(it.bomTool.getBomToolGroupType(), it.environment.getDepth(), nameVersion);
                    return possibility;
                })
                .collect(Collectors.toList());
    }

}