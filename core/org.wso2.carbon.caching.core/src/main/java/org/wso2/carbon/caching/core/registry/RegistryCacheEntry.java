/*
 *  Copyright (c) 2005-2009, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 *
 */
package org.wso2.carbon.caching.core.registry;

import org.wso2.carbon.caching.core.CacheEntry;

/**
 * The container class for the cache entry used in the registry kernel
 */
@SuppressWarnings("unused")
public class RegistryCacheEntry extends CacheEntry {
    private static final long serialVersionUID = -2637223739612208787L;

    private int pathId;

    /**
     * Creates a new entry to be cached.
     *
     * @param pathId the identifier of the path.
     */
    public RegistryCacheEntry(int pathId) {
        this.pathId = pathId;
    }

    /**
     * Method to obtain the identifier of the path.
     *
     * @return the identifier of the path.
     */
    public int getPathId() {
        return pathId;
    }
}
