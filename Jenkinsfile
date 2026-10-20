pipeline {
    agent any

    environment {
        DB_URL            = credentials('EXPENSE_DB_URL')
        DB_USER           = credentials('EXPENSE_DB_USER')
        DB_PASS           = credentials('EXPENSE_DB_PASS')
        TELEGRAM_BOT_USER = credentials('TELEGRAM_BOT_USERNAME')
        TELEGRAM_BOT_TOKEN= credentials('TELEGRAM_BOT_TOKEN')
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
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Deploy Application') {
            steps {
                echo 'Deploying application locally on EC2 host...'
                sh '''
                    mkdir -p /home/ubuntu/expense-tracker
                    cp target/smart-expense-tracker-1.0.0.jar /home/ubuntu/expense-tracker/smart-expense-tracker-1.0.0.jar

                    fuser -k 8081/tcp || true
                    pkill -f smart-expense-tracker-1.0.0.jar || true

                    JENKINS_NODE_COOKIE=dontKillMe nohup java -jar /home/ubuntu/expense-tracker/smart-expense-tracker-1.0.0.jar \
                    --server.port=8081 \
                    --spring.datasource.url="$DB_URL" \
                    --spring.datasource.username="$DB_USER" \
                    --spring.datasource.password="$DB_PASS" \
                    --spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
                    --telegram.bot.username="$TELEGRAM_BOT_USER" \
                    --telegram.bot.token="$TELEGRAM_BOT_TOKEN" > /home/ubuntu/expense-tracker/app.log 2>&1 &
                '''
            }
        }

        stage('Health Check') {
            steps {
                echo 'Verifying application health...'
                sh '''
                    # Give Spring Boot 15 seconds to finish starting up
                    sleep 15

                    # Ping Actuator health endpoint or root application route
                    curl -f http://localhost:8081/actuator/health || curl -f http://localhost:8081/
                '''
            }
        }
    }

    post {
        success {
            echo 'Deployment successful! Application is live on port 8081.'
        }
        failure {
            echo 'Deployment failed. Check application logs at /home/ubuntu/expense-tracker/app.log'
        }
    }
}