package com.task11;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public class CognitoService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private boolean isValidEmail(String email) {
        return email != null && EMAIL_PATTERN.matcher(email).matches();
    }

    public APIGatewayProxyResponseEvent signUp(CognitoIdentityProviderClient cognitoClient, String userPoolId, String body) {
        try {
            Map<String, String> request = objectMapper.readValue(body, HashMap.class);
            String email = request.get("email");
            String password = request.get("password");

            if (!isValidEmail(email)) {
                System.out.println("DEBUG: Invalid email format - " + email);
                return new APIGatewayProxyResponseEvent().withStatusCode(400)
                        .withBody("{\"message\": \"Invalid email format\"}");
            }

            cognitoClient.adminCreateUser(AdminCreateUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .temporaryPassword(password)
                    .messageAction("SUPPRESS")
                    .build());

            cognitoClient.adminSetUserPassword(AdminSetUserPasswordRequest.builder()
                    .userPoolId(userPoolId)
                    .username(email)
                    .password(password)
                    .permanent(true)
                    .build());

            return new APIGatewayProxyResponseEvent().withStatusCode(200);
        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent().withStatusCode(400);
        }
    }

    public APIGatewayProxyResponseEvent signIn(CognitoIdentityProviderClient cognitoClient, String userPoolId, String body) {
        try {
            Map<String, String> request = objectMapper.readValue(body, HashMap.class);
            Map<String, String> authParameters = new HashMap<>();
            authParameters.put("USERNAME", request.get("email"));
            authParameters.put("PASSWORD", request.get("password"));

            AdminInitiateAuthResponse response = cognitoClient.adminInitiateAuth(AdminInitiateAuthRequest.builder()
                    .clientId(System.getenv("CLIENT_ID"))
                    .userPoolId(userPoolId)
                    .authFlow(AuthFlowType.ADMIN_USER_PASSWORD_AUTH)
                    .authParameters(authParameters)
                    .build());

            return new APIGatewayProxyResponseEvent()
                    .withStatusCode(200)
                    .withBody("{\"idToken\": \"" + response.authenticationResult().idToken() + "\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return new APIGatewayProxyResponseEvent().withStatusCode(400);
        }
    }
}