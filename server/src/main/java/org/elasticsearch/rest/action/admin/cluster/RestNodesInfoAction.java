/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.rest.action.admin.cluster;

import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.util.set.Sets;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.RestActions.NodesResponseRestListener;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.elasticsearch.rest.RestRequest.Method.GET;

public class RestNodesInfoAction extends BaseRestHandler {
    static final Set<String> ALLOWED_METRICS = Sets.newHashSet(
            "http",
            "ingest",
            "indices",
            "jvm",
            "os",
            "plugins",
            "process",
            "settings",
            "thread_pool",
            "transport");

    private final SettingsFilter settingsFilter;

    public RestNodesInfoAction(SettingsFilter settingsFilter) {
        this.settingsFilter = settingsFilter;
    }

    @Override
    public List<Route> routes() {
        return List.of(
            new Route(GET, "/_nodes"),
            // this endpoint is used for metrics, not for node IDs, like /_nodes/fs
            new Route(GET, "/_nodes/{nodeId}"),
            new Route(GET, "/_nodes/{nodeId}/{metrics}"),
            // added this endpoint to be aligned with stats
            new Route(GET, "/_nodes/{nodeId}/info/{metrics}"));
    }

    @Override
    public String getName() {
        return "nodes_info_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        final NodesInfoRequest nodesInfoRequest = prepareRequest(request);
        settingsFilter.addFilterSettingParams(request);

        return channel -> client.admin().cluster().nodesInfo(nodesInfoRequest, new NodesResponseRestListener<>(channel));
    }

    static NodesInfoRequest prepareRequest(final RestRequest request) {
        String[] nodeIds;
        Set<String> metrics;

        // special case like /_nodes/os (in this case os are metrics and not the nodeId)
        // still, /_nodes/_local (or any other node id) should work and be treated as usual
        // this means one must differentiate between allowed metrics and arbitrary node ids in the same place
        if (request.hasParam("nodeId") && !request.hasParam("metrics")) {
            String nodeId = request.param("nodeId", "_all");
            Set<String> metricsOrNodeIds = Strings.tokenizeByCommaToSet(nodeId);
            boolean isMetricsOnly = ALLOWED_METRICS.containsAll(metricsOrNodeIds);
            if (isMetricsOnly) {
                nodeIds = new String[]{"_all"};
                metrics = metricsOrNodeIds;
            } else {
                nodeIds = Strings.tokenizeToStringArray(nodeId, ",");
                metrics = Sets.newHashSet("_all");
            }
        } else {
            nodeIds = Strings.tokenizeToStringArray(request.param("nodeId", "_all"), ",");
            metrics = Strings.tokenizeByCommaToSet(request.param("metrics", "_all"));
        }

        final NodesInfoRequest nodesInfoRequest = new NodesInfoRequest(nodeIds);
        nodesInfoRequest.timeout(request.param("timeout"));
        // shortcut, don't do checks if only all is specified
        if (metrics.size() == 1 && metrics.contains("_all")) {
            nodesInfoRequest.all();
        } else {
            nodesInfoRequest.clear();
            nodesInfoRequest.settings(metrics.contains("settings"));
            nodesInfoRequest.os(metrics.contains("os"));
            nodesInfoRequest.process(metrics.contains("process"));
            nodesInfoRequest.jvm(metrics.contains("jvm"));
            nodesInfoRequest.threadPool(metrics.contains("thread_pool"));
            nodesInfoRequest.transport(metrics.contains("transport"));
            nodesInfoRequest.http(metrics.contains("http"));
            nodesInfoRequest.plugins(metrics.contains("plugins"));
            nodesInfoRequest.ingest(metrics.contains("ingest"));
            nodesInfoRequest.indices(metrics.contains("indices"));
        }
        return nodesInfoRequest;
    }

    @Override
    protected Set<String> responseParams() {
        return Settings.FORMAT_PARAMS;
    }

    @Override
    public boolean canTripCircuitBreaker() {
        return false;
    }
}
