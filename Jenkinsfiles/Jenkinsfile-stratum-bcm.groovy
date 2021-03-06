/*
Build Parameters
BUILD_NODE: p4-dev
DOCKER_REGISTRY_IP: 10.128.13.253
DOCKER_REGISTRY_PORT: 5000
*/

pipeline {
    agent {
        label "${BUILD_NODE}"
    }
    options {
        timeout(time: 60, unit: 'MINUTES')
    }
    stages {
        stage('Build, Test and Publish') {
            matrix {
                axes {
                    axis {
                        name 'KERNEL_VERSION'
                        //values '4.9.75', '3.16.56', '4.14.49'
                        values '4.14.49'
                    }
                    axis {
                        name 'SDE'
                        values 'sdklt', 'opennsa'
                    }
                }
                agent {
                    label "${BUILD_NODE}"
                }
                stages {
                    stage("Build") {
                        steps {
                            sh returnStdout: false, label: "Start building stratum-bcm:${SDE}", script: ""
                            build job: "stratum-bcm-build", parameters: [
                                string(name: 'SDE', value: "${SDE}"),
                                string(name: 'DOCKER_REGISTRY_IP', value: "${DOCKER_REGISTRY_IP}"),
                                string(name: 'DOCKER_REGISTRY_PORT', value: "${DOCKER_REGISTRY_PORT}"),
                            ]
                        }
                    }
                    stage('Test') {
                        steps {
                            sh returnStdout: false, label: "Start testing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE}", script: ""
                            build job: "stratum-bcm-test-combined", parameters: [
                                string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE}"),
                                string(name: 'DEBIAN_PACKAGE_PATH', value: "/var/jenkins"),
                                string(name: 'DEBIAN_PACKAGE_NAME', value: "stratum_bcm_${SDE}_deb.deb"),
                                string(name: 'SDE', value: "${SDE}")
                            ]
                        }
                    }
                    stage('Publish') {
                        steps {
                            sh returnStdout: false, label: "Start publishing ${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm:${SDE}", script: ""
                            build job: "stratum-publish", parameters: [
                                string(name: 'DOCKER_IMAGE', value: "${DOCKER_REGISTRY_IP}:${DOCKER_REGISTRY_PORT}/stratum-bcm"),
                                string(name: 'DOCKER_IMAGE_TAG', value: "${SDE}"),
                            ]
                        }
                    }
                }
            }
        }
    }
    post {
        failure {
            slackSend color: 'danger', message: "Test failed: ${env.JOB_NAME} #${env.BUILD_NUMBER} (<${env.RUN_DISPLAY_URL}|Open>)"
        }
    }
}