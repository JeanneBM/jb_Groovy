import hudson.model.*
import hudson.util.*
import jenkins.model.*
import hudson.FilePath.FileCallable
import hudson.slaves.OfflineCause
import hudson.node_monitors.*
import hudson.util.RemotingDiagnostics
import jenkins.model.Jenkins

import java.text.SimpleDateFormat

// Constants for space threshold and job name
def DEFAULT_SPACE = 30
def JOB_NAME = 'master'

def nodesToClean = []
def nodeOutput = ''
int spaceEvaluation = 0

// Fetch the node names, removing irrelevant entries
def getNodeHostnames() {
    return Jenkins.instance.nodes.join(", ")
        .replaceAll('hudson.slaves.DumbSlave', '')
        .replaceAll('hudson.plugins.swarm.SwarmSlave', '')
        .replaceAll('\\[', '')
        .replaceAll('\\]', '')
}

println "[INFO] : The available nodes are: " + getNodeHostnames()

pipeline {
    agent {
        label JOB_NAME
    }

    stages {
        stage('Check Nodes for Disk Space') {
            steps {
                script {
                    checkNodeDiskSpace(Jenkins.instance.nodes)
                }
            }
            post {
                failure {
                    script {
                        env.FAILURE_STAGE = "${env.STAGE_NAME}"
                    }
                }
            }
        }

        stage('Clean Workspaces') {
            steps {
                script {
                    cleanWorkspaces(nodesToClean)
                }
            }
            post {
                failure {
                    script {
                        env.FAILURE_STAGE = "${env.STAGE_NAME}"
                    }
                }
            }
        }
    }
}

// Check disk space for all nodes
def checkNodeDiskSpace(List nodes) {
    nodes.each { node ->
        def computer = node.toComputer()
        def diskSpaceMonitor = DiskSpaceMonitor.DESCRIPTOR.get(computer)

        if (!diskSpaceMonitor) {
            logDiskSpaceIssue(computer, node, "No data available. Check if the node is active.")
        } else {
            handleNodeDiskSpace(node, diskSpaceMonitor)
        }
    }
}

// Handle disk space and node classifications
def handleNodeDiskSpace(node, diskSpaceMonitor) {
    def spaceLeft = getAvailableDiskSpace(diskSpaceMonitor)
    def spaceEvaluation = Integer.parseInt(spaceLeft)

    if (isSpecialNode(node)) {
        println "It's a special node type: ${node.getDisplayName()}"
    } else {
        logDiskSpaceInfo(node, diskSpaceMonitor)

        if (spaceEvaluation < DEFAULT_SPACE) {
            println "# [INFO]: Insufficient free space on node: ${node.getDisplayName()}"
            nodesToClean += node.getDisplayName()
        } else {
            println "# [INFO]: Acceptable space available on node: ${node.getDisplayName()}"
        }
    }
}

// Logs information about the node's disk space
def logDiskSpaceInfo(node, diskSpaceMonitor) {
    println '\n' + "--------------------------------------------------------------------------------------------------------" +
            '\n' + "[INFO]: Checking instance: ${node.getDisplayName()}" +
            '\n' + "[INFO]: Free space: ${diskSpaceMonitor}"
}

// Logs disk space issues
def logDiskSpaceIssue(computer, node, message) {
    println '\n' + "--------------------------------------------------------------------------------------------------------" +
            '\n' + "[INFO]: Checking instance: ${computer}" +
            '\n' + "[INFO]: Node: ${node.getDisplayName()}" +
            '\n' + "[INFO]: ${message}"
}

// Check if the node is classified as a special type
def isSpecialNode(node) {
    return node.getDisplayName().contains("special-node-type") || node.getDisplayName().contains("automation")
}

// Extract available disk space from the monitor
def getAvailableDiskSpace(diskSpaceMonitor) {
    return "${diskSpaceMonitor}".replaceAll('GB.*', '').replaceAll('\\..*', '')
}

// Clean workspaces on the specified nodes
def cleanWorkspaces(List nodesToClean) {
    if (nodesToClean.size() > 0) {
        println "# [INFO]: Nodes requiring workspace cleanup: ${nodesToClean.size()} items."
    } else {
        println "# [INFO]: All nodes meet the requirements. No cleanup needed."
    }

    nodesToClean.eachWithIndex { node, i ->
        println '\n' + "--------------------------------------------------------------------------------------------------------" +
                '\n' + "[INFO]: Cleaning workspace on node: ${node}"

        def groovyScript = getWorkspaceCleanupScript()

        executeCleanupScriptForNode(node, groovyScript)
    }
}

// Generate the workspace cleanup script
def getWorkspaceCleanupScript() {
    return '''#!/bin/bash
        println "uname -a".execute().text
        "cd /data/jenkins_slave/workspace/".execute().text
        println "find . -not \( -path ./maven_local_repository -prune \) -mtime +14 -exec rm -rf {} + ".execute().text
        println "df -h /dev/mapper/datavg-datalv".execute().text
        println "[INFO]: Workspace cleanup completed."
    '''.trim()
}

// Execute cleanup script for the specified node
def executeCleanupScriptForNode(String nodeName, String groovyScript) {
    Jenkins.instance.slaves.find { agent -> agent.name == nodeName }.with { agent ->
        def nodeOutput = RemotingDiagnostics.executeGroovy(groovyScript, agent.channel)
        println nodeOutput
    }
}
