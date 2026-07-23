// system-bank — Jenkins declarative pipeline (Method B hybrid)
// GHA: light PR gate (lint / secrets / quick compile)
// Jenkins: heavy verify + optional Docker package
// Deploy: PHASE 2 only (skipped unless DEPLOY_ENABLED=true AND branch allow-list)

pipeline {
  agent any

  options {
    timestamps()
    ansiColor('xterm')
    disableConcurrentBuilds()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 90, unit: 'MINUTES')
  }

  parameters {
    booleanParam(
      name: 'RUN_PACKAGE',
      defaultValue: false,
      description: 'Build Docker images for backend services (no push/deploy in phase 1)'
    )
    booleanParam(
      name: 'DEPLOY_ENABLED',
      defaultValue: false,
      description: 'PHASE 2 only — do not enable until VPS/registry credentials exist'
    )
  }

  environment {
    JAVA_TOOL_OPTIONS = '-Dfile.encoding=UTF-8'
    MAVEN_OPTS = '-Xmx2g'
    NODE_VERSION = '20'
    // Phase 2 hooks (unused until enabled)
    REGISTRY = "${env.DOCKER_REGISTRY ?: 'ghcr.io'}"
    IMAGE_NAMESPACE = "${env.IMAGE_NAMESPACE ?: 'system-bank'}"
  }

  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script {
          env.GIT_SHA = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
          echo "Building ${env.BRANCH_NAME ?: env.GIT_BRANCH} @ ${env.GIT_SHA}"
        }
        // Keep a marker so docker agents with reuseNode still see workspace
        sh 'ls -la && test -f Jenkinsfile && test -d backend && test -d frontend'
      }
    }

    stage('Secrets check') {
      steps {
        sh '''
          set -eu
          if git ls-files | grep -E '(^|/)\\.env$|infra/\\.env$'; then
            echo "Committed .env files are not allowed"
            git ls-files | grep -E '(^|/)\\.env$|infra/\\.env$'
            exit 1
          fi
          echo "OK: no .env in git index"
        '''
      }
    }

    stage('Backend verify') {
      agent {
        docker {
          image 'maven:3.9.9-eclipse-temurin-21'
          reuseNode true
          args '-v $HOME/.m2:/root/.m2'
        }
      }
      steps {
        dir('backend') {
          sh 'mvn -B -q verify'
        }
      }
    }

    stage('Frontend lint & build') {
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

    stage('Package images') {
      when {
        anyOf {
          expression { return params.RUN_PACKAGE == true }
          allOf {
            branch 'main'
            expression { return env.PACKAGE_ON_MAIN == 'true' }
          }
        }
      }
      steps {
        script {
          // Dockerfiles expect context = backend/ (same as infra/docker-compose.yml)
          def services = [
            'discovery-server',
            'api-gateway',
            'auth-service',
            'customer-service',
            'account-service',
            'transaction-service',
            'notification-service'
          ]
          services.each { svc ->
            def tag = "${IMAGE_NAMESPACE}/${svc}:${GIT_SHA}"
            echo "docker build -t ${tag} -f backend/${svc}/Dockerfile backend"
            sh """
              set -eu
              docker build -t '${tag}' -f 'backend/${svc}/Dockerfile' backend
            """
          }
        }
      }
    }

    stage('Deploy (phase 2)') {
      when {
        allOf {
          expression { return params.DEPLOY_ENABLED == true }
          anyOf {
            branch 'main'
            buildingTag()
          }
        }
      }
      steps {
        echo 'PHASE 2 placeholder: push images + SSH/compose deploy not enabled yet.'
        echo 'Wire credentials (registry, deploy-ssh) then replace this stage.'
        error('Deploy stage is intentionally blocked until phase 2 is configured.')
      }
    }
  }

  post {
    always {
      cleanWs(deleteDirs: true, notFailBuild: true)
    }
    success {
      echo "SUCCESS — ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    }
    failure {
      echo "FAILED — ${env.JOB_NAME} #${env.BUILD_NUMBER}"
    }
  }
}
