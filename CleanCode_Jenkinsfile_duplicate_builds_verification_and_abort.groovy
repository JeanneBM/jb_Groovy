// Main pipeline function that verifies and aborts duplicate builds
pipeline {
    agent any

    stages {
        stage('Builds Verification') {
            steps {
                script {
                    def jobName = 'your-job-name' // Replace with your job name
                    def runningBuilds = getRunningBuilds(jobName)

                    if (runningBuilds.isEmpty()) {
                        println "No running builds found. Verification passed."
                    } else {
                        def duplicatedBuilds = findDuplicatedBuilds(runningBuilds)
                        if (duplicatedBuilds.isEmpty()) {
                            println "No duplicated builds found. Verification passed."
                        } else {
                            println "Duplicated builds found: " + duplicatedBuilds
                            def buildsToAbort = determineBuildsToAbort(duplicatedBuilds)
                            abortBuilds(buildsToAbort)
                        }
                    }
                }
            }
        }
    }
}

// Function to retrieve all running builds
def getRunningBuilds(String jobName) {
    def job = Jenkins.instance.getItemByFullName(jobName)
    return job.getBuilds().findAll { it.getResult() == null }
}

// Function to detect duplicate builds based on their descriptions
def findDuplicatedBuilds(List runningBuilds) {
    def tags = []
    runningBuilds.each { build ->
        def description = build.getDescription().replaceAll(".*your-pattern", "your-pattern")  // Replace with appropriate pattern
        tags += description
    }
    
    tags.sort()
    return findDuplicates(tags)
}

// Helper function to find duplicate entries in a list
def findDuplicates(List tags) {
    def duplicates = []
    def lastTag = null
    tags.each { tag ->
        if (tag == lastTag) {
            duplicates += tag
        }
        lastTag = tag
    }
    return duplicates
}

// Function to determine which builds should be aborted
def determineBuildsToAbort(List duplicatedBuilds) {
    def buildsToAbort = []
    def runningBuilds = Jenkins.instance.getItemByFullName('your-job-name').getBuilds()
    
    runningBuilds.each { build ->
        if (duplicatedBuilds.contains(build.getDescription())) {
            buildsToAbort += build
        }
    }

    // Remove the build with the highest number (it won't be aborted)
    if (buildsToAbort.size() > 1) {
        buildsToAbort.remove(buildsToAbort.max { it.getNumber() })
    }
    
    return buildsToAbort
}

// Function to abort the specified builds
def abortBuilds(List buildsToAbort) {
    buildsToAbort.each { build ->
        if (build.isBuilding()) {
            println "Aborting build: ${build.displayName}"
            build.doStop()
            build.doKill()
        }
    }
}
