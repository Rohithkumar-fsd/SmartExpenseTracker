pipeline {
    agent any

    environment {
        // Application Port (using 8081 so it doesn't conflict with Jenkins on 8080)
        APP_PORT            = '8081'
        DEPLOY_DIR          = '/home/ubuntu/expense-tracker'
        JAR_NAME            = 'smart-expense-tracker-1.0.0.jar'

        // Credentials pulled securely from Jenkins Credential Store
        DB_URL              = credentials('EXPENSE_DB_URL')
        DB_USER             = credentials('EXPENSE_DB_USER')
        DB_PASS             = credentials('EXPENSE_DB_PASS')
        TELEGRAM_BOT_USER   = credentials('TELEGRAM_BOT_USERNAME')
        TELEGRAM_BOT_TOKEN  = credentials('TELEGRAM_BOT_TOKEN')
    }

    stages {
        stage('Checkout Code') {
            steps {
                checkout scm
            }
        }

        stage('Build & Package JAR') {
            steps {
                echo 'Building Spring Boot JAR with Maven...'
                // Using 'sh' because your Jenkins master runs on Linux/EC2
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Deploy Application') {
            steps {
                echo 'Deploying application locally on EC2 host...'
                sh """
                    # 1. Ensure deployment directory exists
                    mkdir -p ${DEPLOY_DIR}

                    # 2. Copy compiled JAR to deployment directory
                    cp target/*.jar ${DEPLOY_DIR}/${JAR_NAME}

                    # 3. Stop any previously running process on port ${APP_PORT}
                    fuser -k ${APP_PORT}/tcp || pkill -f '${JAR_NAME}' || true

                    # 4. Launch updated application in background with environment variables
                    nohup java -jar ${DEPLOY_DIR}/${JAR_NAME} \
                      --server.port=${APP_PORT} \
                      --spring.datasource.url='${DB_URL}' \
                      --spring.datasource.username='${DB_USER}' \
                      --spring.datasource.password='${DB_PASS}' \
                      --telegram.bot.username='${TELEGRAM_BOT_USER}' \
                      --telegram.bot.token='${TELEGRAM_BOT_TOKEN}' \
                      > ${DEPLOY_DIR}/app.log 2>&1 &
                """
            }
        }

        stage('Health Check') {
            steps {
                echo 'Verifying application health...'
                sh """
                    sleep 15
                    curl -f http://localhost:${APP_PORT}/actuator/health || curl -f http://localhost:${APP_PORT}/ || exit 1
                """
            }
        }
    }

    post {
        success {
            echo "Smart Expense Tracker successfully deployed on port ${APP_PORT}!"
        }
        failure {
            echo "Deployment failed. Check application logs at ${DEPLOY_DIR}/app.log"
        }
    }
}