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

import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.hub.detect.bomtool.ExtractionId;
import com.blackducksoftware.integration.hub.detect.bomtool.result.ExceptionBomToolResult;
import com.blackducksoftware.integration.hub.detect.bomtool.search.report.ExtractionReporter;
import com.blackducksoftware.integration.hub.detect.bomtool.search.report.ExtractionSummaryReporter;
import com.blackducksoftware.integration.hub.detect.bomtool.search.report.PreparationSummaryReporter;
import com.blackducksoftware.integration.hub.detect.extraction.model.BomToolEvaluation;
import com.blackducksoftware.integration.hub.detect.manager.result.extraction.ExtractionResult;
import com.blackducksoftware.integration.hub.detect.model.BomToolGroupType;
import com.blackducksoftware.integration.hub.detect.model.DetectCodeLocation;

public class ExtractionManager {
    private final Logger logger = LoggerFactory.getLogger(DetectProjectManager.class);

    private final PreparationSummaryReporter preparationSummaryReporter;
    private final ExtractionSummaryReporter extractionSummaryReporter;
    private final ExtractionReporter extractionReporter;

    public ExtractionManager(final PreparationSummaryReporter preparationSummaryReporter, final ExtractionSummaryReporter extractionSummaryReporter,
            final ExtractionReporter extractionReporter) {
        this.preparationSummaryReporter = preparationSummaryReporter;
        this.extractionSummaryReporter = extractionSummaryReporter;
        this.extractionReporter = extractionReporter;
    }

    private int extractions = 0;

    private void extract(final List<BomToolEvaluation> results) {
        final List<BomToolEvaluation> extractable = results.stream().filter(result -> result.isExtractable()).collect(Collectors.toList());

        for (int i = 0; i < extractable.size(); i++) {
            logger.info("Extracting " + Integer.toString(i + 1) + " of " + Integer.toString(extractable.size()) + " (" + Integer.toString((int) Math.floor((i * 100.0f) / extractable.size())) + "%)");
            extract(extractable.get(i));
        }
    }

    private void prepare(final List<BomToolEvaluation> results) {
        for (final BomToolEvaluation result : results) {
            prepare(result);
        }
    }

    private void prepare(final BomToolEvaluation result) {
        if (result.isApplicable()) {
            try {
                result.extractable = result.bomTool.extractable();
            } catch (final Exception e) {
                result.extractable = new ExceptionBomToolResult(e);
            }
        }
    }

    private void extract(final BomToolEvaluation result) {
        if (result.isExtractable()) {
            extractions++;
            final ExtractionId extractionId = new ExtractionId(Integer.toString(extractions));
            extractionReporter.startedExtraction(result.bomTool, extractionId);
            result.extraction = result.bomTool.extract(extractionId);
            extractionReporter.endedExtraction(result.extraction);
        }

    }

    public ExtractionResult performExtractions(final List<BomToolEvaluation> bomToolEvaluations) {
        prepare(bomToolEvaluations);

        preparationSummaryReporter.print(bomToolEvaluations);

        extract(bomToolEvaluations);

        final HashSet<BomToolGroupType> succesfulBomTools = new HashSet<>();
        final HashSet<BomToolGroupType> failedBomTools = new HashSet<>();
        for (final BomToolEvaluation evaluation : bomToolEvaluations) {
            final BomToolGroupType type = evaluation.bomTool.getBomToolGroupType();
            if (evaluation.isApplicable()) {
                if (evaluation.isExtractable() && evaluation.isExtractionSuccess()) {
                    succesfulBomTools.add(type);
                } else {
                    failedBomTools.add(type);
                }
            }
        }

        final List<DetectCodeLocation> codeLocations = bomToolEvaluations.stream()
                .filter(it -> it.isExtractionSuccess())
                .flatMap(it -> it.extraction.codeLocations.stream())
                .collect(Collectors.toList());

        final ExtractionResult result = new ExtractionResult(codeLocations, succesfulBomTools, failedBomTools);
        return result;
    }

}