package com.example.spring_dynamodb_implementation.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.spring_dynamodb_implementation.entity.User;
import com.example.spring_dynamodb_implementation.exception.UsernotFoundException;
import com.example.spring_dynamodb_implementation.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User createUser(User user){
        if(user.getId() == null || user.getId().isBlank()){
            user.setId(UUID.randomUUID().toString());
        }
        userRepository.save(user);
        log.info("User created successfully with id: " + user.getId());
        return user;
    }

    public User getUserById(String id) {
        log.info("Getting user with id: " + id);
        return userRepository.findById(id)
                .orElseThrow(() -> new UsernotFoundException("User not found with id: " + id));
    }

    public List<User> getAllUsers() {
        log.info("Getting all users");
        return userRepository.findAll();
    }

    public User updateUser(String id, User updatedUser) {
        log.info("Updating user with id: " + id);
        getUserById(id); // Ensure item exists
        updatedUser.setId(id);
        log.info("User updated successfully with id: " + id);
        return userRepository.update(updatedUser);
    }

    public void deleteUser(String id) {
        log.info("Deleting user with id: " + id);
        userRepository.deleteById(id);
    }   
}
