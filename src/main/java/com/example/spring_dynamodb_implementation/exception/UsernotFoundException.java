package com.example.spring_dynamodb_implementation.exception;

public class UsernotFoundException extends RuntimeException {
    public UsernotFoundException(String message) {
        super(message);
    }
}
