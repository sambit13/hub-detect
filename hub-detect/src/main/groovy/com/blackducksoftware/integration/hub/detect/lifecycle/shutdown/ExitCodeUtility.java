/**
 * hub-detect
 *
 * Copyright (C) 2019 Black Duck Software, Inc.
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
package com.blackducksoftware.integration.hub.detect.lifecycle.shutdown;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.hub.detect.exception.DetectUserFriendlyException;
import com.blackducksoftware.integration.hub.detect.exitcode.ExitCodeType;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.synopsys.integration.blackduck.exception.BlackDuckApiException;
import com.synopsys.integration.exception.IntegrationException;
import com.synopsys.integration.rest.exception.IntegrationRestException;

public class ExitCodeUtility {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private static String BLACDUCK_ERROR_MESSAGE = "An unrecoverable error occurred - most likely this is due to your environment and/or configuration. Please double check the Detect documentation: https://blackducksoftware.atlassian.net/wiki/x/Y7HtAg";

    public ExitCodeType getExitCodeFromExceptionDetails(final Exception e) {
        final ExitCodeType exceptionExitCodeType;

        if (e instanceof DetectUserFriendlyException) {
            if (e.getCause() != null) {
                logger.debug(e.getCause().getMessage(), e.getCause());
            }
            final DetectUserFriendlyException friendlyException = (DetectUserFriendlyException) e;
            exceptionExitCodeType = friendlyException.getExitCodeType();
        } else if (e instanceof BlackDuckApiException) {
            BlackDuckApiException be = (BlackDuckApiException) e;

            logger.error("A Black Duck Api exception was thrown.");
            logger.error(be.getMessage());
            logger.debug(be.getBlackDuckErrorCode());
            logger.error(be.getOriginalIntegrationRestException().getMessage());

            exceptionExitCodeType = ExitCodeType.FAILURE_BLACKDUCK_FEATURE_ERROR;
        } else if (e instanceof IntegrationRestException) {
            logger.error(BLACDUCK_ERROR_MESSAGE);
            logger.debug(e.getMessage(), e);

            exceptionExitCodeType = ExitCodeType.FAILURE_BLACKDUCK_FEATURE_ERROR;
        } else if (e instanceof IntegrationException) {
            logger.error(BLACDUCK_ERROR_MESSAGE);
            logger.debug(e.getMessage(), e);
            exceptionExitCodeType = ExitCodeType.FAILURE_GENERAL_ERROR;
        } else {
            logger.error("An unknown/unexpected error occurred");
            if (e.getMessage() != null) {
                logger.error(e.getMessage());
            } else if (e instanceof NullPointerException) {
                logger.error("Null Pointer Exception");
            } else {
                logger.error(e.getClass().getSimpleName());
            }
            if (e.getStackTrace().length >= 1) {
                logger.error("Thrown at " + e.getStackTrace()[0].toString());
            }
            exceptionExitCodeType = ExitCodeType.FAILURE_UNKNOWN_ERROR;
        }
        if (e.getMessage() != null) {
            logger.error(e.getMessage());
        }

        return exceptionExitCodeType;
    }
}
