pipeline {
    agent any

    options {
        disableConcurrentBuilds()
        timestamps()
    }

    parameters {
        booleanParam(name: 'PRUNE_IMAGES', defaultValue: false,
            description: 'Ungenutzte Docker-Images nach Deploy aufräumen (docker image prune -f)')
    }

    environment {
        COMPOSE_DIR = 'deploy'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Prepare Secrets') {
            steps {
                // .env liegt dauerhaft außerhalb des Workspaces auf dem Server (nicht im
                // Repo, siehe .env.example) -- Checkout kann das Verzeichnis neu anlegen,
                // deshalb hier bei jedem Build frisch reinkopieren statt auf einen
                // einmalig gesetzten Symlink im Workspace zu vertrauen.
                sh '''
                    ENV_SRC="/home/dockerserver/docker/cookie/repo/deploy/.env"
                    if [ ! -f "$ENV_SRC" ]; then
                        echo "Fehlt: $ENV_SRC -- DB_PASS/ADMIN_TOKEN/STEAM_WEB_API_KEY sind dort zu setzen (siehe deploy/.env.example)"
                        exit 1
                    fi
                    cp "$ENV_SRC" "${COMPOSE_DIR}/.env"
                '''
            }
        }

        stage('Build Images') {
            steps {
                dir(env.COMPOSE_DIR) {
                    sh 'docker compose build'
                }
            }
        }

        stage('Deploy') {
            steps {
                dir(env.COMPOSE_DIR) {
                    // .env liegt direkt auf dem Server (nicht im Repo, siehe .env.example)
                    sh 'docker compose up -d --remove-orphans'
                }
            }
        }

        stage('Health Check') {
            steps {
                sh '''
                    for i in $(seq 1 30); do
                        if curl -sf http://localhost:9876/api/v1/config > /dev/null 2>&1; then
                            echo "Backend healthy"
                            exit 0
                        fi
                        sleep 2
                    done
                    echo "Backend nicht erreichbar nach 60s"
                    exit 1
                '''
            }
        }

        stage('Cleanup') {
            when {
                expression { params.PRUNE_IMAGES }
            }
            steps {
                sh 'docker image prune -f'
            }
        }
    }

    post {
        failure {
            dir(env.COMPOSE_DIR) {
                sh 'docker compose logs --tail=100 || true'
            }
        }
    }
}
