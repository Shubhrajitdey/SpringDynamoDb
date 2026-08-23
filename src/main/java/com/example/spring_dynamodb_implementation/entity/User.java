package com.example.spring_dynamodb_implementation.entity;

import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@Setter
public class User {
    private String id;
    private String name;
    private String email;
    private String department;

    @DynamoDbPartitionKey
    @DynamoDbAttribute("id")
    public String getId(){
        return id;
    }
    @DynamoDbAttribute("name")
    public String getName() {
        return name;
    }

    @DynamoDbAttribute("email")
    public String getEmail() {
        return email;
    }

    @DynamoDbAttribute("department")
    public String getDepartment() {
        return department;
    }
}
