package com.retailzw.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaasLoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;
}

