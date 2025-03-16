// Constants for configuration
final String LDAP_ADDRESS = '168.168.168.168'
final String JIRA_API_URL = 'https://example.com/jira/rest/api/1'
final String LDAP_BASE_DN = 'dc=example,dc=com'
final String LDAP_ADMIN_DN = 'cn=admin,dc=example,dc=com'

// Pipeline definition
pipeline {
    agent { label 'ansible' }

    environment {
        EMAIL_CONTENT_FAILURE = "${env.EMAIL_DEFAULT_CONTENT_FAILURE}"
            .replace("{env.JOB_NAME}", "${env.JOB_NAME}")
            .replace("{env.BUILD_NUMBER}", "${env.BUILD_NUMBER}")
            .replace("{env.BUILD_URL}", "${env.BUILD_URL}")
            .replace("{env.NODE_NAME}", "${env.NODE_NAME}")

        EMAIL_CONTENT_SUCCESS = "${env.EMAIL_DEFAULT_CONTENT_SUCCESS}"
            .replace("{env.JOB_NAME}", "${env.JOB_NAME}")
            .replace("{env.BUILD_NUMBER}", "${env.BUILD_NUMBER}")
            .replace("{env.BUILD_URL}", "${env.BUILD_URL}")
            .replace("{env.NODE_NAME}", "${env.NODE_NAME}")
    }

    stages {
        stage('Clean Workspace') {
            steps {
                cleanWorkspace()
            }
        }

        stage('List All LDAP Groups') {
            when { expression { env.LABEL == '[ALL_GROUPS]' } }
            steps {
                script {
                    def allGroups = fetchAllLdapGroups()
                    logInfo("All LDAP Groups: ${allGroups}")
                    appendToJiraComment("All LDAP Groups: ${allGroups}")
                }
            }
        }

        stage('Verify Email Addresses') {
            when { expression { env.LABEL == '[EMAIL_LIST]' } }
            steps {
                script {
                    def users = extractUsersFromDescription(env.DESCRIPTION)
                    users.each { user ->
                        def email = fetchUserEmail(user)
                        logInfo("Email for ${user}: ${email}")
                        appendToJiraComment("Email for ${user}: ${email}")
                    }
                }
            }
        }

        stage('Add Groups to LDAP') {
            when { expression { env.LABEL == '[GROUP_ADD]' } }
            steps {
                script {
                    def newGroups = extractGroupsFromDescription(env.DESCRIPTION)
                    def existingGroups = fetchAllLdapGroups()
                    newGroups.each { group ->
                        if (existingGroups.contains(group)) {
                            logInfo("Group ${group} already exists in LDAP.")
                            appendToJiraComment("Group ${group} already exists in LDAP.")
                        } else {
                            addGroupToLdap(group)
                            appendToJiraComment("Group ${group} added to LDAP.")
                        }
                    }
                }
            }
        }

        stage('Update Jira Issue') {
            steps {
                script {
                    updateJiraIssue(env.ISSUE_KEY, jiraComment)
                }
            }
        }
    }

    post {
        success {
            sendEmailNotification("SUCCESS", env.EMAIL_CONTENT_SUCCESS)
        }
        failure {
            sendEmailNotification("FAILURE", env.EMAIL_CONTENT_FAILURE)
        }
    }
}

// Helper Functions

/**
 * Cleans the workspace by removing all files and directories.
 */
def cleanWorkspace() {
    sh "cd ${pwd()} && find -maxdepth 1 -mindepth 1 -exec rm -rf {} \\;"
}

/**
 * Fetches all LDAP groups from the directory.
 */
def fetchAllLdapGroups() {
    withCredentials([usernamePassword(credentialsId: 'ldap-credentials', passwordVariable: 'ldapPassword', usernameVariable: 'ldapUsername')]) {
        return sh(returnStdout: true, script: """
            ldapsearch -h ${LDAP_ADDRESS} -p 389 -D '${LDAP_ADMIN_DN}' -w ${ldapPassword} -LLL -b 'ou=groups,${LDAP_BASE_DN}' cn | grep 'cn:'
        """).replace("cn:", "").trim().tokenize().toList().sort()
    }
}

/**
 * Extracts user information from the Jira description.
 */
def extractUsersFromDescription(String description) {
    return description.toLowerCase().split('\\r\\n|\\n|\\r').findAll { it.startsWith('user') }.collect {
        it.replace("user:", "").trim()
    }
}

/**
 * Fetches the email address of a user from LDAP.
 */
def fetchUserEmail(String user) {
    withCredentials([usernamePassword(credentialsId: 'ldap-credentials', passwordVariable: 'ldapPassword', usernameVariable: 'ldapUsername')]) {
        return sh(returnStdout: true, script: """
            ldapsearch -h ${LDAP_ADDRESS} -p 389 -D '${LDAP_ADMIN_DN}' -w ${ldapPassword} -LLL -b 'ou=people,${LDAP_BASE_DN}' 'cn=${user}' mail | grep mail:
        """).replace("mail:", "").trim()
    }
}

/**
 * Extracts group names from the Jira description.
 */
def extractGroupsFromDescription(String description) {
    return description.split('\\r\\n|\\n|\\r').findAll { it.startsWith('group') }.collect {
        it.replace("group:", "").replaceAll("[^a-zA-Z0-9,-_]", "").trim()
    }.unique()
}

/**
 * Adds a new group to LDAP.
 */
def addGroupToLdap(String group) {
    withCredentials([usernamePassword(credentialsId: 'ldap-credentials', passwordVariable: 'ldapPassword', usernameVariable: 'ldapUsername')]) {
        sh(script: """
            ldapadd -h ${LDAP_ADDRESS} -p 389 -D '${LDAP_ADMIN_DN}' -w ${ldapPassword} -f ${group}_add_group.ldif
        """)
    }
}

/**
 * Updates the Jira issue with a comment.
 */
def updateJiraIssue(String issueKey, String comment) {
    withCredentials([usernamePassword(credentialsId: 'jira-credentials', passwordVariable: 'jiraPassword', usernameVariable: 'jiraUsername')]) {
        sh(script: """
            curl -D- -u ${jiraUsername}:${jiraPassword} -X POST --data '{"body": "${comment}"}' -H 'Content-Type: application/json' ${JIRA_API_URL}/issue/${issueKey}/comment
        """)
    }
}

/**
 * Logs an informational message.
 */
def logInfo(String message) {
    println "[INFO]: ${message}"
}

/**
 * Appends a message to the Jira comment.
 */
def appendToJiraComment(String message) {
    jiraComment += "${message}\n"
}

/**
 * Sends an email notification.
 */
def sendEmailNotification(String status, String content) {
    emailext(
        subject: "[${status}] Job '${env.JOB_NAME} [${env.BUILD_NUMBER}]'",
        body: content,
        mimeType: 'text/html',
        to: "example@example.com"
    )
}
