import hudson.model.*
import hudson.util.*
import jenkins.model.*
import hudson.FilePath.FileCallable
import hudson.slaves.OfflineCause
import hudson.node_monitors.*
import hudson.util.RemotingDiagnostics
import jenkins.model.Jenkins

import java.text.SimpleDateFormat

def defaultSpace = 30
def nodesHostname = Jenkins.instance.nodes.join(", ").replaceAll('hudson.slaves.DumbSlave', '').replaceAll('hudson.plugins.swarm.SwarmSlave', '').replaceAll('\\[', '').replaceAll('\\]', '')
def nodes = nodesHostname
def nodesToClean = []
def communicat = []
def nodeOutput = ''
int spaceEvaluation = 0

println "[INFO] : The available nodes are: " + nodesHostname

pipeline {
    agent {
        label 'master'
    }

    stages {
        stage('Checking Slaves Workspaces') {
            steps {
                script {
                    for (node in Jenkins.instance.nodes) {
                        computer = node.toComputer()
                        diskSpaceMonitor = DiskSpaceMonitor.DESCRIPTOR.get(computer)

                        if (diskSpaceMonitor == null) {
                            println '\n' +
                                "--------------------------------------------------------------------------------------------------------" +
                                '\n' + '\n' +
                                "###################### [INFO]: Checking instance:  [" + computer + "] Node:  [" + node.getDisplayName() + "]" +
                                '\n' +
                                "###################### [INFO]: No data available. Check if the node is active"
                            '\n' + '\n'
                        } else if (node.getDisplayName().contains("special-node-type")) {
                            println "It's a special node type"
                        } else if (node.getDisplayName().contains("automation")) {
                            println "It's an automation node."
                        } else {
                            println '\n' +
                                "--------------------------------------------------------------------------------------------------------" +
                                '\n' + '\n' +
                                "###################### [INFO]: Checking instance:  [" + computer + "] Node:  [" + node.getDisplayName() + "]" +
                                '\n' +
                                "###################### [INFO]: Free space: " + diskSpaceMonitor +
                                '\n' + '\n'

                            spaceLeft = "${diskSpaceMonitor}".replaceAll('GB.*', '').replaceAll('\\..*', '')
                            spaceEvaluation = Integer.parseInt(spaceLeft)

                            if (spaceEvaluation < defaultSpace) {
                                println "# [INFO]: Insufficient free space on node: [" + node.getDisplayName() + "]"
                                nodesToClean += node.getDisplayName()
                            } else {
                                println "# [INFO]: Acceptable space available."
                            }
                        }
                    }
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

        stage('Clearing Workspaces') {
            steps {
                script {
                    def i = 0

                    if (nodesToClean.size() > 0) {
                        println "# [INFO]: Nodes requiring workspace cleanup [" + nodesToClean.size() + " item(s)]: " + nodesToClean
                    } else {
                        println "# [INFO]: All nodes comply with the requirements. Nothing to do."
                    }

                    nodesToClean.each {

                        println '\n' +
                            "--------------------------------------------------------------------------------------------------------" +
                            '\n' + '\n' +
                            "###################### [INFO]: Cleaning instance: " + nodesToClean[i]
                        groovy_script = '''#!/bin/bash
                        println "uname -a".execute().text
                        "cd /data/jenkins_slave/workspace/".execute().text
                        println "find . -not \( -path ./maven_local_repository -prune \) -mtime +14 -exec rm -rf {} + ".execute().text
                        println "df -h /dev/mapper/datavg-datalv".execute().text
                        println "###################### [INFO]: Workspace cleanup has been done. The instance meets the expected requirements."
                        '''.trim()

                        Jenkins.instance.slaves.find { agent ->
                            agent.name == nodesToClean[i]
                        }.with { agent ->
                            nodeOutput = RemotingDiagnostics.executeGroovy(groovy_script, agent.channel)
                        }
                        println nodeOutput
                        i += 1
                    }
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
