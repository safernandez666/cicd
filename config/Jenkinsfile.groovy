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
                            int code = sh returnStatus: true, script: """ gitleaks-linux-amd64 --repo-path=/var/jenkins_home/workspace/$JOB_NAME/ --pretty -v """
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
                        sh 'sh /opt/dependency-check/bin/dependency-check.sh --scan /var/jenkins_home/workspace/$JOB_NAME/app --format ALL'
                        dependencyCheckPublisher pattern: '**/dependency-check-report.xml'
                        sleep(time:5,unit:"SECONDS")
                    }
                }
                stage('SonarQube') {
                    steps {
                        echo "Analisis SonarQube"
                        sh "/opt/sonar-scanner/bin/sonar-scanner -Dsonar.host.url=http://sonarqube:9000 -Dsonar.projectName=04.DevSecOps -Dsonar.projectVersion=1.0 -Dsonar.projectKey=04.DevSecOps -Dsonar.sources=. -Dsonar.projectBaseDir=/var/jenkins_home/workspace/04.DevSecOps_Script/app -Dsonar.login=7159403361daf6cac6225fea1117f5f745ee4ce8"
                        sleep(time:10,unit:"SECONDS")
                    }
                }
            }
        }
        stage ('Build & Push to DockerHub') {
            steps {
                sh """ 
                cd app
                docker build -t app .
                docker tag app:latest safernandez666/app:latest
                docker push safernandez666/app:latest
                docker rmi app safernandez666/app 
                 """        // Shell build step
             }
         }   
         stage ('Deploy Over Kubernetes') {
            steps {
                kubernetesDeploy(kubeconfigId: 'Okteto',               // REQUIRED

                 configs: 'deployment/deployment.yaml', // REQUIRED
                 enableConfigSubstitution: false,
                 )
            }
        }
        stage('DAST') {
            steps {
                script {
                    //sh "docker exec zap zap-cli --verbose quick-scan http://pipeline.ironbox.com.ar:8090 -l Medium" 
                    try {
                        echo "Inicio de Scanneo Dinamico"
                        sh "docker exec zap zap-cli --api-key 5364864132243598723485 --port 8000 --verbose quick-scan https://flaskapp-deployment-safernandez666.cloud.okteto.net/" 
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
