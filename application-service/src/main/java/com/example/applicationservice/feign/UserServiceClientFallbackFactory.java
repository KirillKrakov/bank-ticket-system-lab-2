package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class UserServiceClientFallbackFactory implements FallbackFactory<UserServiceClient> {
    @Override
    public UserServiceClient create(Throwable cause) {
        return new UserServiceClient() {
            @Override
            public Boolean userExists(UUID id) {
                return false;
            }

            @Override
            public UserRole getUserRole(UUID id) {
                return UserRole.ROLE_CLIENT;
            }
        };
    }
}