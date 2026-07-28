// system-bank — Enterprise HDBank-Style Modular Jenkins Pipeline
// Optimized for Low RAM / Selective Service Build / Auto Git-Diff Detection

pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')
  }

  parameters {
    string(
      name: 'BRANCH_NAME',
      defaultValue: 'main',
      description: 'Git branch / tag / commit to build'
    )
    choice(
      name: 'TARGET_SCOPE',
      choices: [
        'AUTO',
        'ALL',
        'FE',
        'account-service',
        'auth-service',
        'customer-service',
        'transaction-service',
        'notification-service',
        'api-gateway',
        'discovery-server',
        'BE-ALL'
      ],
      description: 'Select target service to build. AUTO = auto-detect changed files via Git diff.'
    )
    booleanParam(
      name: 'SKIP_TESTS',
      defaultValue: true,
      description: 'Skip Unit Tests for fast build (Set to false for full test verification)'
    )
    booleanParam(
      name: 'RUN_PACKAGE',
      defaultValue: true,
      description: 'Build Docker Image for selected target service(s)'
    )
    booleanParam(
      name: 'RESTART_CONTAINER',
      defaultValue: true,
      description: 'Restart local Docker container after packaging'
    )
  }

  environment {
    JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
    MAVEN_OPTS = '-Xmx1g -XX:+UseG1GC'
    NODE_VERSION = '20'
    GIT_CREDENTIALS_ID = "${env.GIT_CREDENTIALS_ID ?: 'github-pat'}"
  }

  stages {
    stage('Checkout & Detect Scope') {
      steps {
        script {
          def ref = params.BRANCH_NAME?.trim() ?: 'main'
          echo "On-demand modular build for ref: ${ref} | Target: ${params.TARGET_SCOPE}"

          checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${ref}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
              [$class: 'CloneOption', shallow: false, noTags: false, honorRefspec: true],
              [$class: 'CleanBeforeCheckout']
            ],
            userRemoteConfigs: [[
              url: 'https://github.com/phi1235/system-bank.git',
              credentialsId: env.GIT_CREDENTIALS_ID,
              refspec: "+refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/*"
            ]]
          ])

          env.GIT_SHA = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()

          // Customize Build Display Name & Description (Enterprise HDBank Style)
          def buildUser = env.BUILD_USER ?: "Nguyen Chau Phi"
          def artifactTag = "${env.BUILD_NUMBER}-${env.GIT_SHA}"
          currentBuild.displayName = "#${env.BUILD_NUMBER} [${ref}]"
          currentBuild.description = "Branch: ${ref}\nPipeline: CI CD\nUser: ${buildUser}\nArtifactTag: ${artifactTag}"
          
          // Determine targets to build
          def scope = params.TARGET_SCOPE
          def buildFe = false
          def buildServices = [] as Set

          if (scope == 'ALL') {
            buildFe = true
            buildServices = ['account-service', 'auth-service', 'customer-service', 'transaction-service', 'notification-service', 'api-gateway', 'discovery-server'] as Set
          } else if (scope == 'FE') {
            buildFe = true
          } else if (scope == 'BE-ALL') {
            buildServices = ['account-service', 'auth-service', 'customer-service', 'transaction-service', 'notification-service', 'api-gateway', 'discovery-server'] as Set
          } else if (scope != 'AUTO') {
            buildServices = [scope] as Set
          } else {
            // AUTO detect via git diff against HEAD~1 or origin/main
            echo "Auto-detecting changed files via git diff..."
            def changedFiles = sh(
              returnStdout: true,
              script: "git diff --name-only HEAD~1 HEAD 2>/dev/null || git diff --name-only origin/main...HEAD 2>/dev/null || echo ''"
            ).trim().split('\n')

            echo "Changed files count: ${changedFiles.size()}"
            changedFiles.each { file ->
              if (file.startsWith('frontend/')) {
                buildFe = true
              } else if (file.startsWith('backend/account-service/')) {
                buildServices.add('account-service')
              } else if (file.startsWith('backend/auth-service/')) {
                buildServices.add('auth-service')
              } else if (file.startsWith('backend/customer-service/')) {
                buildServices.add('customer-service')
              } else if (file.startsWith('backend/transaction-service/')) {
                buildServices.add('transaction-service')
              } else if (file.startsWith('backend/notification-service/')) {
                buildServices.add('notification-service')
              } else if (file.startsWith('backend/api-gateway/')) {
                buildServices.add('api-gateway')
              } else if (file.startsWith('backend/discovery-server/')) {
                buildServices.add('discovery-server')
              } else if (file.startsWith('backend/common-lib/') || file == 'backend/pom.xml') {
                buildServices = ['account-service', 'auth-service', 'customer-service', 'transaction-service', 'notification-service', 'api-gateway', 'discovery-server'] as Set
              }
            }

            // Fallback if no files matched or first build
            if (!buildFe && buildServices.isEmpty()) {
              echo "No specific changes detected or single commit. Defaulting to full check."
              buildFe = true
              buildServices = ['account-service', 'auth-service', 'customer-service', 'transaction-service', 'notification-service', 'api-gateway', 'discovery-server'] as Set
            }
          }

          env.DO_BUILD_FE = buildFe.toString()
          env.TARGET_SERVICES = buildServices.join(',')
          echo "Build Matrix Decision -> Frontend: ${env.DO_BUILD_FE} | Backend Services: ${env.TARGET_SERVICES}"
        }
      }
    }

    stage('Backend Build (Targeted)') {
      when {
        expression { return env.TARGET_SERVICES != null && env.TARGET_SERVICES != '' }
      }
      agent {
        docker {
          image 'maven:3.9.9-eclipse-temurin-21'
          args '-v /var/jenkins_home/.m2:/root/.m2'
          reuseNode true
        }
      }
      steps {
        script {
          def servicesList = env.TARGET_SERVICES.split(',')
          def mavenProjectsParam = servicesList.collect { "-pl ${it}" }.join(' ')
          def skipTestsFlag = params.SKIP_TESTS ? '-Dmaven.test.skip=true' : ''

          echo "Running targeted Maven build for: ${servicesList}..."
          dir('backend') {
            sh "mvn -B clean package ${mavenProjectsParam} -am ${skipTestsFlag}"
          }
        }
      }
    }

    stage('Frontend Build (Targeted)') {
      when {
        expression { return env.DO_BUILD_FE == 'true' }
      }
      agent {
        docker {
          image 'node:20-bookworm'
          reuseNode true
        }
      }
      steps {
        dir('frontend/bank-angular-app') {
          sh '''
            set -eu
            npm ci
            npm run lint
            npm run build
          '''
        }
      }
    }

    stage('Package Docker Images') {
      when {
        expression { return params.RUN_PACKAGE == true && env.TARGET_SERVICES != '' }
      }
      steps {
        script {
          def servicesList = env.TARGET_SERVICES.split(',')
          servicesList.each { svc ->
            def imageTag = "bank-system-${svc}:latest"
            echo "Building Docker image: ${imageTag}..."
            sh "DOCKER_BUILDKIT=1 docker build -t '${imageTag}' -f 'backend/${svc}/Dockerfile' backend"
          }
        }
      }
    }

    stage('Restart Target Container(s)') {
      when {
        expression { return params.RESTART_CONTAINER == true && env.TARGET_SERVICES != '' }
      }
      steps {
        script {
          def containerMap = [
            'account-service': 'bank-account',
            'auth-service': 'bank-auth',
            'customer-service': 'bank-customer',
            'transaction-service': 'bank-transaction',
            'notification-service': 'bank-notification',
            'api-gateway': 'bank-gateway',
            'discovery-server': 'bank-discovery'
          ]
          def servicesList = env.TARGET_SERVICES.split(',')
          servicesList.each { svc ->
            def containerName = containerMap[svc]
            if (containerName) {
              echo "Starting/Restarting local container: ${containerName}..."
              sh "docker start ${containerName} 2>/dev/null || docker restart ${containerName} || echo 'Container ${containerName} not found'"
            }
          }
        }
      }
    }
  }

  post {
    always {
      cleanWs(deleteDirs: true, notFailBuild: true)
    }
    success {
      echo "SUCCESS — ref=${params.BRANCH_NAME} sha=${env.GIT_SHA} Targets=[FE:${env.DO_BUILD_FE}, BE:${env.TARGET_SERVICES}]"
    }
    failure {
      echo "FAILED — ref=${params.BRANCH_NAME} #${env.BUILD_NUMBER}"
    }
  }
}
