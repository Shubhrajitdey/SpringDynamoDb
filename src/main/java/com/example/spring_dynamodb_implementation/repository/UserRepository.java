package com.example.spring_dynamodb_implementation.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import com.example.spring_dynamodb_implementation.entity.User;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.waiters.DynamoDbWaiter;

@Repository
@RequiredArgsConstructor
public class UserRepository {
    private DynamoDbTable<User> userTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;

    /*
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
                // AWS initiates the table creation in the cloud, but it takes 5 to 15 seconds for AWS
                // to provision hardware and transition the table status from Creating → Active
                // Without waiter.waitUntilTableExists(), your application would try to perform CRUD operations
                // immediately after calling createTable() which trigger the Resource not found.
                waiter.waitUntilTableExists(DescribeTableRequest.builder().tableName("User").build());
            }
        } catch (ResourceInUseException e) {
            // Table already exists in AWS DynamoDB
        }
    }


    public void save(User user) {
        userTable.putItem(user);
    }

    public Optional<User> findById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        return Optional.ofNullable(userTable.getItem(key));
    }

    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        userTable.scan().items().forEach(users::add);
        return users;
    }

    public User update(User user) {
        return userTable.updateItem(user);
    }

    public void deleteById(String id) {
        Key key = Key.builder().partitionValue(id).build();
        userTable.deleteItem(key);
    }
}
