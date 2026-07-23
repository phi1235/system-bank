// system-bank — Jenkins declarative pipeline (Method B hybrid)
// GHA: light PR gate (lint / secrets / quick compile)
// Jenkins: heavy verify + optional Docker package — ON DEMAND per chosen branch
//
// Intended job type: "Pipeline" (NOT Multibranch)
// - Parameter BRANCH_NAME: branch/tag/commit to build (you type it)
// - No auto-scan of every feature branch
// Deploy: PHASE 2 only (blocked until configured)

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
    string(
      name: 'BRANCH_NAME',
      defaultValue: 'main',
      description: 'Git branch / tag / commit to build (only this ref runs — not all branches)'
    )
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
    REGISTRY = "${env.DOCKER_REGISTRY ?: 'ghcr.io'}"
    IMAGE_NAMESPACE = "${env.IMAGE_NAMESPACE ?: 'system-bank'}"
    // Jenkins credential ID for private GitHub clone (Manage Jenkins → Credentials).
    // Override on the job: prepare an env var GIT_CREDENTIALS_ID, or rename credential to github-pat.
    GIT_CREDENTIALS_ID = "${env.GIT_CREDENTIALS_ID ?: 'github-pat'}"
  }

  stages {
    stage('Checkout') {
      steps {
        script {
          def ref = params.BRANCH_NAME?.trim()
          if (!ref) {
            error('BRANCH_NAME is required. Enter the branch you want to build (e.g. main, feature/xxx).')
          }
          echo "On-demand build for ref: ${ref}"

          // Explicit checkout so one Pipeline job can target any branch without Multibranch
          checkout([
            $class: 'GitSCM',
            branches: [[name: "*/${ref}"]],
            doGenerateSubmoduleConfigurations: false,
            extensions: [
              [$class: 'CloneOption', shallow: true, depth: 1, noTags: false, honorRefspec: true],
              [$class: 'CleanBeforeCheckout']
            ],
            userRemoteConfigs: [[
              url: 'https://github.com/phi1235/system-bank.git',
              credentialsId: env.GIT_CREDENTIALS_ID,
              refspec: "+refs/heads/*:refs/remotes/origin/* +refs/tags/*:refs/tags/*"
            ]]
          ])

          env.GIT_SHA = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
          env.RESOLVED_BRANCH = sh(
            returnStdout: true,
            script: "git rev-parse --abbrev-ref HEAD 2>/dev/null || echo '${ref}'"
          ).trim()
          echo "Checked out ${ref} @ ${env.GIT_SHA} (HEAD=${env.RESOLVED_BRANCH})"
        }
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
        expression { return params.RUN_PACKAGE == true }
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
        expression { return params.DEPLOY_ENABLED == true }
      }
      steps {
        echo 'PHASE 2 placeholder: push images + SSH/compose deploy not enabled yet.'
        echo "Would deploy ref=${params.BRANCH_NAME} sha=${env.GIT_SHA}"
        error('Deploy stage is intentionally blocked until phase 2 is configured.')
      }
    }
  }

  post {
    always {
      cleanWs(deleteDirs: true, notFailBuild: true)
    }
    success {
      echo "SUCCESS — ref=${params.BRANCH_NAME} sha=${env.GIT_SHA} #${env.BUILD_NUMBER}"
    }
    failure {
      echo "FAILED — ref=${params.BRANCH_NAME} #${env.BUILD_NUMBER}"
    }
  }
}
