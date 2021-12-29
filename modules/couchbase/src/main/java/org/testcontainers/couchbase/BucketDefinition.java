/*
 * Copyright (c) 2020 Couchbase, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.testcontainers.couchbase;

import java.util.ArrayList;
import java.util.List;

/**
 * Allows to configure the properties of a bucket that should be created.
 */
public class BucketDefinition {

    private final String name;
    private final List<ScopeDefinition> scopes = new ArrayList<>();

    private boolean flushEnabled = false;
    private boolean queryPrimaryIndex = true;
    private int quota = 100;
    private BucketType bucketType = BucketType.COUCHBASE;

    public BucketDefinition(final String name) {
        this.name = name;
    }

    /**
     * Enables flush for this bucket (disabled by default).
     *
     * @param flushEnabled if true, the bucket can be flushed.
     * @return this {@link BucketDefinition} for chaining purposes.
     */
    public BucketDefinition withFlushEnabled(final boolean flushEnabled) {
        this.flushEnabled = flushEnabled;
        return this;
    }

    /**
     * Sets a custom bucket quota (100MB by default).
     *
     * @param quota the quota to set for the bucket.
     * @return this {@link BucketDefinition} for chaining purposes.
     */
    public BucketDefinition withQuota(final int quota) {
        if (quota < 100) {
          throw new IllegalArgumentException("Bucket quota cannot be less than 100MB!");
        }
        this.quota = quota;
        return this;
    }

    /**
     * Allows to disable creating a primary index for this bucket (enabled by default).
     *
     * @param create if false, a primary index will not be created.
     * @return this {@link BucketDefinition} for chaining purposes.
     */
    public BucketDefinition withPrimaryIndex(final boolean create) {
        this.queryPrimaryIndex = create;
        return this;
    }

    /**
     * Adds a scope (with its collections) to this bucket - only available with 7.0 and later.
     *
     * @param scope the scope with its collections.
     * @return this {@link BucketDefinition} for chaining purposes.
     */
    public BucketDefinition withScope(final ScopeDefinition scope) {
        this.scopes.add(scope);
        return this;
    }

    /**
     * Allows to customize the bucket type.
     * <p>
     * IMPORTANT: if you are using the community edition AND the query service with ephemeral buckets, you need
     * to make sure to use at least 7.0.2 community edition or later - earlier versions will NOT work. Default
     * couchbase buckets are not affected by this version constraint.
     *
     * @param bucketType the type of bucket that should be created.
     * @return this {@link BucketDefinition} for chaining purposes.
     */
    public BucketDefinition withBucketType(final BucketType bucketType) {
        this.bucketType = bucketType;
        return this;
    }

    public String getName() {
        return name;
    }

    public boolean hasFlushEnabled() {
        return flushEnabled;
    }

    public boolean hasPrimaryIndex() {
        return queryPrimaryIndex;
    }

    public int getQuota() {
        return quota;
    }

    public List<ScopeDefinition> getScopes() {
        return scopes;
    }

    public BucketType getBucketType() {
        return bucketType;
    }

    /**
     * Specifies the type of bucket that can be created.
     * <p>
     * Note that the memcached bucket type is not provided, since it is already deprecated on the server side. Please
     * use ephemeral buckets instead.
     */
    public enum BucketType {
        /**
         * The default bucket type, that supports all features and comes with persistence.
         */
        COUCHBASE("couchbase"),
        /**
         * In-memory bucket type, with no persistence. Supports all features but Views.
         * <p>
         * IMPORTANT: if you are using the community edition AND the query service with ephemeral buckets, you need
         * to make sure to use at least 7.0.2 community edition or later - earlier versions will NOT work.
         */
        EPHEMERAL("ephemeral");

        private final String identifier;

        BucketType(String identifier) {
            this.identifier = identifier;
        }

        public String getIdentifier() {
            return identifier;
        }
    }

}
