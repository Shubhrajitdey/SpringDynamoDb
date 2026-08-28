# Spring Boot DynamoDB Integrationn

A modern RESTful web service built with **Spring Boot 3** and **AWS SDK for Java v2 (DynamoDB Enhanced Client)** to perform full CRUD operations against Amazon DynamoDB.

---

## 🚀 Features

- **AWS SDK v2 Enhanced Client**: Object-oriented mapping between Java entity models and DynamoDB items.
- **Dual Environment Support**: Easily switch between **Local Docker DynamoDB** (`http://localhost:8000`) and **AWS Cloud DynamoDB**.
- **Automated & Manual Table Provisioning**: Supports both programmatic table creation on startup with `DynamoDbWaiter` and pre-created AWS tables.
- **RESTful API**: Full CRUD endpoints (`CREATE`, `READ`, `UPDATE`, `DELETE`).
- **Global Exception Handling**: Centralized handling of custom exceptions using `@ControllerAdvice`.
- **Default Credentials Chain**: Seamless authentication locally via `~/.aws/credentials` or on AWS infrastructure via IAM Roless.

---

## 🛠️ Tech Stack

- **Java**: 17
- **Framework**: Spring Boot 3.xx
- **AWS SDK**: AWS SDK for Java v2 (`dynamodb`, `dynamodb-enhanced`)
- **Lombok**: Boilerplate code reduction
- **Build Tool**: Maven

---

## 📋 Prerequisites

Before running the application, ensure you have:

1. **Java 17+** and **Maven** installed.
2. **Docker** installed (if running DynamoDB locally).
3. **AWS Credentials** configured on your local machine via AWS CLI (if running against AWS Cloud):
   ```bash
   aws configure
   ```
   *This creates `~/.aws/credentials` and `~/.aws/config` containing your Access Key ID and Secret Access Key.*

---

## ⚙️ Configuration & Environment Switching

Application settings are managed in `src/main/resources/application.properties`:

```properties
spring.application.name=spring-dynamodb-implementation

# AWS DynamoDB Configuration
aws.region=ap-south-1
aws.dynamodb.tableName=User

# Local DynamoDB (Docker) Endpoint Override
# Uncomment the line below to connect to local DynamoDB in Docker on port 8000:
# aws.endpoint=http://localhost:8000
```

### 1. Running Against Local DynamoDB (Docker)
1. Start the DynamoDB Local Docker container on port `8000`:
   ```bash
   docker run -p 8000:8000 amazon/dynamodb-local
   ```
2. In `application.properties`, uncomment:
   ```properties
   aws.endpoint=http://localhost:8000
   ```

### 2. Running Against AWS Cloud DynamoDB
1. Leave `aws.endpoint` commented out or empty in `application.properties`.
2. The AWS SDK will automatically route requests to `dynamodb.ap-south-1.amazonaws.com` using your local AWS CLI credentials.

---

## 💻 Key Code Snippets & Architecture Reference

### 1. Endpoint Override in `DynamoDbConfig.java`
Allows seamless switching between Docker (`http://localhost:8000`) and AWS Cloud:

```java
@Configuration
public class DynamoDbConfig {

    @Value("${aws.region:ap-south-1}")
    private String region;

    @Value("${aws.endpoint:}")
    private String endpoint;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create());

        // Optional endpoint override for Local Testing with Docker (http://localhost:8000)
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
```

---

### 2. Table Provisioning with `DynamoDbWaiter` in `UserRepository.java`

```java
@Repository
@RequiredArgsConstructor
public class UserRepository {
    private DynamoDbTable<User> userTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    /* Alternative Constructor Injection pattern:
    public UserRepository(DynamoDbEnhancedClient enhancedClient, 
                          @Value("${aws.dynamodb.tableName:User}") String tableName) {
        this.userTable = enhancedClient.table(tableName, TableSchema.fromBean(User.class));
    }
    */

    @PostConstruct
    public void init() {
        userTable = enhancedClient.table("User", TableSchema.fromBean(User.class));
        try {
            userTable.createTable();
            try (DynamoDbWaiter waiter = dynamoDbClient.waiter()) {
                // AWS initiates table creation in the cloud, but it takes 5 to 15 seconds for AWS
                // to provision hardware and transition table status from Creating -> Active.
                // Without waiter.waitUntilTableExists(), your application would try to perform CRUD operations
                // immediately after calling createTable() which triggers ResourceNotFoundException.
                waiter.waitUntilTableExists(DescribeTableRequest.builder().tableName("User").build());
            }
        } catch (ResourceInUseException e) {
            // Table already exists in AWS DynamoDB
        }
    }
    // ...
}
```

---

### 3. Alternative Pattern: Spring `@Profile` Configuration (`@Profile("local")` vs `@Profile("dev")`)

An alternative clean pattern is using Spring's `@Profile` annotation to create environment-specific `DynamoDbEnhancedClient` beans based on the active profile (`local` vs `dev`/`prod`).

#### How to Activate Profiles in `application.properties`:
```properties
# To activate the local profile:
spring.profiles.active=local

# To activate the dev profile:
# spring.profiles.active=dev
```
*Or via command line*: `./mvnw spring-boot:run -Dspring-boot.run.profiles=local` or `-Dspring.profiles.active=dev`.

#### Profile Configuration Class Reference (`DynamoDBEnhancedConfig.java`):

```java
package com.codesnippet.S3DemoApplication.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDBEnhancedConfig {

    @Value("${cloud.aws.region.static}")
    private String region;

    // Active when spring.profiles.active=local
    @Bean
    @Profile("local")
    public DynamoDbEnhancedClient dynamoDbEnhancedClientLocal(
            @Value("${cloud.aws.credentials.access-key}") String accessKey,
            @Value("${cloud.aws.credentials.secret-key}") String secretKey) {
        
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)
                ))
                .build();

        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    // Active when spring.profiles.active=dev (or prod)
    @Bean
    @Profile("dev")
    public DynamoDbEnhancedClient dynamoDbEnhancedClientDev() {
        DynamoDbClient dynamoDbClient = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();

        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
```

---

## 🗄️ DynamoDB Table Provisioning Options

### Option A: Manual Table Creation (AWS Console / Terraform / CLI)
In production environments, tables are typically managed via Infrastructure as Code (IaC) or the AWS Console.

- **AWS Console Steps**:
  1. Open AWS DynamoDB Console (Region: `ap-south-1`).
  2. Click **Create table**.
  3. **Table Name**: `User`
  4. **Partition Key**: `id` (String)
  5. Select **On-Demand** or **Provisioned** capacity and click **Create table**.

### Option B: Programmatic Initialization with `@PostConstruct` & `DynamoDbWaiter`
For local development or integration testing, the application can create the table automatically on startup using AWS SDK v2 Enhanced Client (detailed above).

#### Why `DynamoDbWaiter` is Crucial
- **Asynchronous Table Provisioning**: Calling `userTable.createTable()` is asynchronous in AWS Cloud. The API call returns immediately while AWS provisions the hardware in the background (~5–15 seconds).
- **Preventing `ResourceNotFoundException`**: If an incoming HTTP request hits your REST controller before the table reaches `ACTIVE` status, DynamoDB throws a `ResourceNotFoundException`.
- **Synchronous Block via Waiter**: `waiter.waitUntilTableExists()` polls AWS until the table status transitions to `ACTIVE`, ensuring that Tomcat only starts accepting API requests once the table is fully ready.

---

## 🏃 Getting Started

### 1. Clone the repository
```bash
git clone <repository-url>
cd spring-dynamodb-implementation
```

### 2. Build the project
```bash
./mvnw clean compile
```

### 3. Run the application
```bash
./mvnw spring-boot:run
```

The server will start on `http://localhost:8080`.

---

## 📑 API Endpoints

### Base URL: `/api/v1/users`

| Method | Endpoint | Description | Status Code |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/v1/users` | Create a new user | `201 Created` |
| **GET** | `/api/v1/users/{id}` | Get user by ID | `200 OK` / `404 Not Found` |
| **GET** | `/api/v1/users` | Get list of all users | `200 OK` |
| **PUT** | `/api/v1/users/{id}` | Update existing user by ID | `200 OK` / `404 Not Found` |
| **DELETE**| `/api/v1/users/{id}` | Delete user by ID | `204 No Content` |

---

## 📝 API Request & Response Examples

### 1. Create User
- **HTTP Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/users`
- **Request Body**:
```json
{
  "name": "Bittu Dey",
  "email": "bittu@example.com",
  "department": "Engineering"
}
```
- **Response** (`201 Created`):
```json
{
  "id": "e5b7c8a9-1234-4567-890a-bcdef1234567",
  "name": "Bittu Dey",
  "email": "bittu@example.com",
  "department": "Engineering"
}
```

---

### 2. Get User By ID
- **HTTP Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/users/USER%231`

> ⚠️ **Important URL Encoding Note**:
> If your ID contains special characters like `#` (e.g. `USER#1`), you **must** URL-encode `#` as `%23` in Postman or cURL (`USER%231`). Raw `#` is interpreted by HTTP clients as an anchor fragment and will be stripped before reaching the server.

- **Response** (`200 OK`):
```json
{
  "id": "USER#1",
  "name": "Bittu Dey",
  "email": "bittu@example.com",
  "department": "Engineering"
}
```

---

### 3. Update User
- **HTTP Method**: `PUT`
- **URL**: `http://localhost:8080/api/v1/users/USER%231`
- **Request Body**:
```json
{
  "name": "Bittu Dey",
  "email": "bittu.updated@example.com",
  "department": "DevOps"
}
```
- **Response** (`200 OK`):
```json
{
  "id": "USER#1",
  "name": "Bittu Dey",
  "email": "bittu.updated@example.com",
  "department": "DevOps"
}
```

---

### 4. Delete User
- **HTTP Method**: `DELETE`
- **URL**: `http://localhost:8080/api/v1/users/USER%231`
- **Response**: `204 No Content`

---

## 🏗️ Project Architecture

```text
src/main/java/com/example/spring_dynamodb_implementation/
├── config/
│   └── DynamoDbConfig.java            # Spring Configuration for DynamoDbClient & DynamoDbEnhancedClient
├── controller/
│   └── UserController.java            # REST Controller exposing endpoints
├── dto/                               # Data Transfer Objects
├── entity/
│   └── User.java                      # @DynamoDbBean entity with table mapping
├── exception/
│   ├── GlobalExceptionHandler.java    # Centralized @ControllerAdvice
│   └── UsernotFoundException.java     # Custom 404 RuntimeException
├── repository/
│   └── UserRepository.java            # DynamoDbTable operations & auto table creation with Waiter
└── service/
    └── UserService.java               # Business logic layer with logging
```

---

## 🔒 Security & Best Practices

- **Zero Hardcoded Secrets**: Credentials are automatically resolved via AWS SDK's `DefaultCredentialsProvider` chain (`~/.aws/credentials`).
- **Immutable Primary Keys**: In DynamoDB, Partition Keys (`id`) are immutable. Updating a record retains its key and updates non-key attributes.
