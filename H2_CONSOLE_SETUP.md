# Wallet Event Processor - Database Access Guide

## Current Issue: H2 Console at `http://localhost:8080/h2-console`

The H2 console endpoint (`/h2-console`) is currently not accessible due to a **Jakarta EE compatibility issue** in Spring Boot 4.1.1. The H2 library uses `javax.servlet.*` packages, while Spring Boot 6.x (included with Spring Boot 4.1.1) uses `jakarta.servlet.*` packages. These are not compatible.

## ✅ How to Access Your H2 Database

### Option 1: Using H2 Console Standalone Application (Recommended)

1. Download H2 console from: https://www.h2database.com/html/download.html
2. Extract the archive
3. Run the console:
   ```bash
   java -jar h2/bin/h2-2.x.x.jar
   ```
4. A browser window will open. Connect with:
   - **JDBC URL**: `jdbc:h2:mem:testdb`
   - **User Name**: `sa`
   - **Password**: (leave empty)

### Option 2: Using IntelliJ IDEA Database Viewer

1. Open your project in IntelliJ IDEA
2. Go to: **View → Tool Windows → Database**
3. Click the **+** icon → **Data Source** → **H2**
4. Configure:
   - **URL**: `jdbc:h2:mem:testdb`
   - **User**: `sa`
   - **Password**: (leave empty)
5. Click **Test Connection** → **OK**
6. Browse your database tables in the tool window

### Option 3: Using DBeaver (Free)

1. Download DBeaver from: https://dbeaver.io/download/
2. Create a new H2 database connection:
   - **Connection Type**: H2
   - **URL**: `jdbc:h2:mem:testdb`
   - **User**: `sa`
   - **Password**: (leave empty)
3. Test and connect

### Option 4: Using H2 Shell Command Line

1. Get H2 JAR (should be in your Maven repository):
   ```bash
   ~/.m2/repository/com/h2database/h2/*/h2-*.jar
   ```
2. Run shell:
   ```bash
   java -cp ~/.m2/repository/com/h2database/h2/2.x.x/h2-2.x.x.jar org.h2.tools.Shell
   ```
3. Connect with:
   ```
   url: jdbc:h2:mem:testdb
   user: sa
   password: (leave empty)
   ```

### Option 5: Using Spring Boot Actuator (Add to Dependencies)

1. Add to `pom.xml`:
   ```xml
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   ```
2. Application metrics and info available at `http://localhost:8080/actuator`

## Database Configuration

**File**: `src/main/resources/application.properties`

```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.jpa.hibernate.ddl-auto=create-drop
```

- **Database**: In-memory H2 (created on startup, destroyed on shutdown)
- **User**: `sa` (System Administrator)
- **Password**: Empty (no password required)
- **Tables**: Auto-created via JPA `@Entity` classes

## Application Health Check

Verify the application is running:
```bash
curl http://localhost:8080/api/v1/health
```

Response:
```json
{
  "status": "UP",
  "message": "Wallet Event Processor is running",
  "endpoints": {
    "process_payment": "/api/v1/transactions/process (POST)",
    "h2_console": "See README for H2 console setup instructions"
  }
}
```

## Accessing the Wallet API

**Endpoint**: `http://localhost:8080/api/v1/transactions/process`

**Method**: POST

**Example Request**:
```bash
curl -X POST http://localhost:8080/api/v1/transactions/process \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId": "550e8400-e29b-41d4-a716-446655440000",
    "userId": "550e8400-e29b-41d4-a716-446655440001",
    "amount": "100.00",
    "type": "DEBIT"
  }'
```

## Database Tables

After running the application, the following tables will be created:

- `wallet` - User wallet information (userId, balance, timestamps)
- `wallet_event` - Transaction audit log (eventType, amount, idempotency keys, status)

You can query these tables using any of the H2 console options listed above.

---

**Note**: The in-memory H2 database (`jdbc:h2:mem:testdb`) will be reset every time the application restarts. For persistent storage, change the URL to:
```properties
spring.datasource.url=jdbc:h2:file:~/wallet_database
```
