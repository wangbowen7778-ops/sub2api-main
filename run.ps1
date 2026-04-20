$env:DB_HOST = "localhost"
$env:DB_PORT = "5432"
$env:DB_NAME = "sub2api"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "admin@123"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
$env:REDIS_PASSWORD = "admin@123"
$env:JWT_SECRET = "change-me-in-production-must-be-at-least-256-bits-long"

mvn spring-boot:run -Dspring-boot.run.profiles=dev 2>&1
