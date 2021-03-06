/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.kafka.trogdor.fault;

import org.apache.kafka.trogdor.common.JsonUtil;
import org.apache.kafka.trogdor.common.Node;
import org.apache.kafka.trogdor.common.Platform;
import org.apache.kafka.trogdor.common.Topology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public class NetworkPartitionFault implements Fault {
    private static final Logger log = LoggerFactory.getLogger(NetworkPartitionFault.class);

    private final String id;
    private final NetworkPartitionFaultSpec spec;
    private final List<Set<String>> partitions;

    public NetworkPartitionFault(String id, FaultSpec spec) {
        this.id = id;
        this.spec = (NetworkPartitionFaultSpec) spec;
        this.partitions = new ArrayList<>();
        HashSet<String> prevNodes = new HashSet<>();
        for (List<String> partition : this.spec.partitions()) {
            for (String nodeName : partition) {
                if (prevNodes.contains(nodeName)) {
                    throw new RuntimeException("Node " + nodeName +
                        " appears in more than one partition.");
                }
                prevNodes.add(nodeName);
                this.partitions.add(new HashSet<String>(partition));
            }
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public FaultSpec spec() {
        return spec;
    }

    @Override
    public void activate(Platform platform) throws Exception {
        log.info("Activating NetworkPartitionFault...");
        runIptablesCommands(platform, "-A");
    }

    @Override
    public void deactivate(Platform platform) throws Exception {
        log.info("Deactivating NetworkPartitionFault...");
        runIptablesCommands(platform, "-D");
    }

    private void runIptablesCommands(Platform platform, String iptablesAction) throws Exception {
        Node curNode = platform.curNode();
        Topology topology = platform.topology();
        TreeSet<String> toBlock = new TreeSet<>();
        for (Set<String> partition : partitions) {
            if (!partition.contains(curNode.name())) {
                for (String nodeName : partition) {
                    toBlock.add(nodeName);
                }
            }
        }
        for (String nodeName : toBlock) {
            Node node = topology.node(nodeName);
            InetAddress addr = InetAddress.getByName(node.hostname());
            platform.runCommand(new String[] {
                "sudo", "iptables", iptablesAction, "INPUT", "-p", "tcp", "-s",
                addr.getHostAddress(), "-j", "DROP", "-m", "comment", "--comment", nodeName
            });
        }
    }

    @Override
    public Set<String> targetNodes(Topology topology) {
        Set<String> targetNodes = new HashSet<>();
        for (Set<String> partition : partitions) {
            targetNodes.addAll(partition);
        }
        return targetNodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkPartitionFault that = (NetworkPartitionFault) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(spec, that.spec) &&
            Objects.equals(partitions, that.partitions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, spec, partitions);
    }

    @Override
    public String toString() {
        return "NoOpFault(id=" + id + ", spec=" + JsonUtil.toJsonString(spec) + ")";
    }
}
