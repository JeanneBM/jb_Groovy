// Initializing variables for build verification
def buildDescription = ''
def duplicatedTags = ''
def tags = []
def duplicatedBuilds = []
def descriptionsList = []
def toAbortBuilds = []
def i = 0

// Defining the pipeline
pipeline {
    agent any
    
    stages {
        // Stage to verify running builds
        stage('Builds Verification') {
            steps {
                script {
                    // Load all running builds of the selected job
                    def jobName = 'your-job-name'  // Replace with your actual job name
                    def runningBuilds = Jenkins.getInstance().getItemByFullName(jobName).getBuilds().findAll { it.getResult() == null }
                    
                    if (runningBuilds == "[]") {
                        println "############ [INFO]: No other builds are running at the moment. Positive verification."
                    } else if (runningBuilds.size() == 1) {
                        println "############ [INFO]: Only one build is running. Positive verification." + '\n' + runningBuilds + '\n' + runningBuilds[0].getDescription()
                    } else {
                        println "Currently running builds: " + runningBuilds
                        
                        // Creating a list of all descriptions of the running builds
                        runningBuilds.each { 
                            buildDescription = it.getDescription().replaceAll(".*your-pattern", "your-pattern")  // Replace with your actual pattern
                            tags += buildDescription
                        }
                        
                        // Sorting the tags to find duplicates
                        tags = tags.sort()
                        i = 0
                        tags.each { 
                            if (tags[i] == tags[i - 1]) {
                                duplicatedTags += tags[i]
                            }
                            i += 1
                        }

                        if (duplicatedTags.size() != 0) {
                            println "############ [INFO]: The duplicated tags are: " + duplicatedTags
                        } else {
                            println "############ [INFO]: No duplicated tags. Positive verification."
                        }

                        // Verification of duplicated tags in the running builds
                        i = 0
                        int j = 0
                        
                        runningBuilds.each {
                            duplicatedTags.each {
                                buildDescription = it.getDescription().replaceAll(".*your-pattern", "your-pattern")  // Replace with your actual pattern
                                if (buildDescription == duplicatedTags[j]) {
                                    duplicatedBuilds += runningBuilds[i]
                                    duplicatedBuilds = duplicatedBuilds.sort()

                                    // According to the agreement, all except the build with the highest number should be aborted
                                    def max = duplicatedBuilds.size() - 1
                                    duplicatedBuilds = duplicatedBuilds - duplicatedBuilds[max]
                                    toAbortBuilds += duplicatedBuilds
                                }
                                j += 1
                            }
                            i += 1
                        }
                        
                        println "The builds that should be aborted: " + toAbortBuilds.sort()
                        toAbortBuilds = toAbortBuilds.replaceAll("your-job-name #", "")  // Replace with your actual job name
                    }
                }
            }
        }

        // Stage to abort builds
        stage('Aborting Builds') {
            steps {
                script {
                    println "Triggering the aborting of builds..."
                    
                    def job = Jenkins.instance.getItemByFullName('your-job-name')  // Replace with your actual job name
                    job.builds.each { build ->
                        if (toAbortBuilds.contains(build.getNumber().toString())) {
                            if (build.isBuilding()) {
                                build.doStop()
                                build.doKill()
                            }
                        }
                    }
                }
            }
        }
    }
}
