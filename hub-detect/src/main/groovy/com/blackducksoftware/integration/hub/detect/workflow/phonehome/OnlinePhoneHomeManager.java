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
package com.blackducksoftware.integration.hub.detect.workflow.phonehome;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.blackducksoftware.integration.hub.detect.DetectInfo;
import com.blackducksoftware.integration.hub.detect.workflow.event.EventSystem;
import com.google.gson.Gson;
import com.synopsys.integration.blackduck.phonehome.BlackDuckPhoneHomeHelper;
import com.synopsys.integration.phonehome.PhoneHomeResponse;

public class OnlinePhoneHomeManager extends PhoneHomeManager {
    private final Logger logger = LoggerFactory.getLogger(OnlinePhoneHomeManager.class);

    private BlackDuckPhoneHomeHelper blackDuckPhoneHomeHelper;

    public OnlinePhoneHomeManager(Map<String, String> additionalMetaData, final DetectInfo detectInfo, final Gson gson, EventSystem eventSystem, final BlackDuckPhoneHomeHelper blackDuckPhoneHomeHelper) {
        super(additionalMetaData, detectInfo, gson, eventSystem);

        this.blackDuckPhoneHomeHelper = blackDuckPhoneHomeHelper;
    }

    @Override
    public PhoneHomeResponse phoneHome(final Map<String, String> metadata) {
        Map<String, String> metaDataToSend = new HashMap<>();
        metaDataToSend.putAll(metadata);
        metaDataToSend.putAll(additionalMetaData);
        return blackDuckPhoneHomeHelper.handlePhoneHome("hub-detect", detectInfo.getDetectVersion(), metaDataToSend);
    }

}
