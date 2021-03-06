pipeline {
    agent any
    options {
        buildDiscarder(logRotator(numToKeepStr: '3'))
    }
    stages {
        stage('GitHub') {
            steps {
                echo "Clonacion del Proyecto en GitHub"
                checkout([$class: 'GitSCM', branches: [[name: '*/main']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '', url: 'https://github.com/safernandez666/cicd.git']]])
            }
        }
        stage('SAST') {
            parallel {
                stage('Check GitLeaks') {
                    steps { 
                        echo "Analisis Leaks"       
                        script {
                            int code = sh returnStatus: true, script: """ gitleaks --path=/var/jenkins_home/workspace/$JOB_NAME -v --leaks-exit-code=1 """
                            if(code==1) {
                                currentBuild.result = 'FAILURE'
                                error('Contraseñas en el Codigo.')
                                println "UNESTABLE"
                            }
                            else {
                                currentBuild.result = 'SUCCESS' 
                                println "Sin Contraseñas en el Codigo."
                                println "SUCCESS"
                            }   
                        }         
                    }
                }
                stage('Dependency Check') {
                    steps {
                        echo "Analisis de Dependencias"
                        sh 'sh /opt/dependency-check/bin/dependency-check.sh --scan /var/jenkins_home/workspace/$JOB_NAME/app --format ALL -n'
                        dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                        sleep(time:5,unit:"SECONDS")
                    }
                }
                stage('SonarQube') {
                    steps {
                        echo "Analisis SonarQube"
                        sh "/opt/sonar-scanner/bin/sonar-scanner -Dsonar.host.url=http://sonarqube:9000 -Dsonar.projectKey=App -Dsonar.projectVersion=1.0 -Dsonar.sources=. -Dsonar.projectBaseDir=/var/jenkins_home/workspace/$JOB_NAME/app -Dsonar.login=0d7ff4d672320acbb10209b897dcf4379b02bfc9"
                        sleep(time:10,unit:"SECONDS")
                    }
                }
            }
        }
        stage ('Build Image') {
            steps {
                sh """ 
                cd app
                docker build -t app:v1.$BUILD_ID .
                docker tag app:v1.$BUILD_ID safernandez666/app:v1.$BUILD_ID
                docker tag app:v1.$BUILD_ID safernandez666/app:latest
                 """        // Shell build step
             }
        }
        stage('Docker Analisys With Trivy AquaSec') {
            steps { 
                script {
                    int code = sh returnStatus: true, script: """ trivy --exit-code 1 --severity CRITICAL,HIGH safernandez666/app:v1.$BUILD_ID """
                    if(code==1) {
                        currentBuild.result = 'FAILURE'
                        error('El Docker posee Vulnerabilidades.')
                        println "UNESTABLE"
                    }
                    else {
                        currentBuild.result = 'SUCCESS' 
                        println "El Docker no posee Vulnerabilidades."
                        println "SUCCESS"
                    }   
                }         
            }
        }
        stage ('Push To DockerHub') {
            steps {
                sh """ 
                docker push safernandez666/app:latest
                docker push safernandez666/app:v1.$BUILD_ID
                docker rmi app:v1.$BUILD_ID safernandez666/app:latest
                 """        // Shell build step
             }
        }
        stage ('Deploy Over Kubernetes') {
            steps {
                kubernetesDeploy(kubeconfigId: 'Okteto',               // REQUIRED

                 configs: 'deployment/deployment.yaml', // REQUIRED
                 enableConfigSubstitution: true
                 )
            }
        }
        stage('DAST') {
            steps {
                script {
                    //sh "docker exec zap zap-cli --verbose quick-scan http://pipeline.ironbox.com.ar:8090 -l Medium" 
                    try {
                        echo "Inicio de Scanneo Dinamico"
                        sh "docker exec zap zap-cli --api-key 5364864132243598723485 --port 8000 --verbose quick-scan https://flaskapp-deployment-safernandez666.cloud.okteto.net -l High"
                        //sh "docker exec zap zap-cli --verbose alerts --alert-level Medium -f json | jq length"
                        currentBuild.result = 'SUCCESS' 
                    }
                    catch (Exception e) {
                            //echo e.getMessage() 
                            //currentBuild.result = 'FAILURE'
                            println ("Revisar Reporte ZAP. Se encontraron Vulnerabilidades.")

                        }
                    }  
                    echo currentBuild.result 
                    echo "Generacion de Reporte"
                    sh "docker exec zap zap-cli --api-key 5364864132243598723485 --port 8000 --verbose report -o /zap/reports/owasp-quick-scan-report.html --output-format html"
                    publishHTML target: [
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: '/var/jenkins_home',
                    reportFiles: 'owasp-quick-scan-report.html',
                    reportName: 'Analisis DAST'
                    ]         
                }
            }  
        } 
    }
