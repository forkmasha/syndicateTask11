package com.task11;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.syndicate.deployment.annotations.environment.EnvironmentVariable;
import com.syndicate.deployment.annotations.environment.EnvironmentVariables;
import com.syndicate.deployment.annotations.lambda.LambdaHandler;
import com.syndicate.deployment.annotations.resources.DependsOn;
import com.syndicate.deployment.model.ResourceType;
import com.syndicate.deployment.model.RetentionSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.util.Optional;

import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_CLIENT_ID;
import static com.syndicate.deployment.model.environment.ValueTransformer.USER_POOL_NAME_TO_USER_POOL_ID;

@LambdaHandler(
        lambdaName = "api_handler",
        roleName = "api_handler-role",
        isPublishVersion = true,
        aliasName = "${lambdas_alias_name}",
        logsExpiration = RetentionSetting.SYNDICATE_ALIASES_SPECIFIED
)
@DependsOn(resourceType = ResourceType.COGNITO_USER_POOL, name = "${booking_userpool}")
@EnvironmentVariables(value = {
        @EnvironmentVariable(key = "REGION", value = "${region}"),
        @EnvironmentVariable(key = "tables_table", value = "${tables_table}"),
        @EnvironmentVariable(key = "reservations_table", value = "${reservations_table}"),
        @EnvironmentVariable(key = "COGNITO_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_USER_POOL_ID),
        @EnvironmentVariable(key = "CLIENT_ID", value = "${booking_userpool}", valueTransformer = USER_POOL_NAME_TO_CLIENT_ID)
})

public class ApiHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {


    private final CognitoService cognitoService = new CognitoService();
    private final DatabaseService databaseService = new DatabaseService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent event, Context context) {
        String resource = event.getResource();
        String httpMethod = event.getHttpMethod();
        String body = event.getBody();
        String tableId = event.getPathParameters() != null ? event.getPathParameters().getOrDefault("tableId", "") : "";

        System.out.println("DEBUG: COGNITO_ID = " + System.getenv("COGNITO_ID"));
        System.out.println("DEBUG: CLIENT_ID = " + System.getenv("CLIENT_ID"));
        System.out.println("DEBUG: REGION = " + System.getenv("REGION"));


        try (CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(System.getenv("REGION"))).build()) {

            String userPoolId = System.getenv("COGNITO_ID");
            System.out.println("DEBUG: Using User Pool ID: " + userPoolId);




            System.err.println("ERROR: User Pool ID is missing or not set correctly! Value: " + userPoolId);
            System.err.println("ERROR: COGNITO_ID environment variable might be missing or misconfigured.");

            System.out.println("DEBUG: Incoming request details:");
            System.out.println("DEBUG: Resource - " + resource);
            System.out.println("DEBUG: HTTP Method - " + httpMethod);
            System.out.println("DEBUG: Request Body - " + body);

            switch (resource) {
                case "/signup":
                    System.out.println("DEBUG: Calling signUp() function");
                    return HttpMethod.POST.toString().equals(httpMethod)
                            ? cognitoService.signUp(cognitoClient, userPoolId, body)
                            : errorResponse();

                case "/signin":
                    System.out.println("DEBUG: Calling signIn() function");
                    return HttpMethod.POST.toString().equals(httpMethod)
                            ? cognitoService.signIn(cognitoClient, userPoolId, body)
                            : errorResponse();

                case "/tables":
                    System.out.println("DEBUG: Calling createTable() or getTables() function");
                    return HttpMethod.POST.toString().equals(httpMethod)
                            ? databaseService.createTable(body)
                            : databaseService.getTables();

                case "/tables/{tableId}":
                    System.out.println("DEBUG: Calling getTableById() function with tableId: " + tableId);
                    return HttpMethod.GET.toString().equals(httpMethod)
                            ? databaseService.getTableById(tableId)
                            : errorResponse();

                case "/reservations":
                    System.out.println("DEBUG: Calling createReservation() or getReservations() function");
                    return HttpMethod.POST.toString().equals(httpMethod)
                            ? databaseService.createReservation(body)
                            : databaseService.getReservations();

                default:
                    System.err.println("ERROR: Unknown API resource requested - " + resource);
                    return errorResponse();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return errorResponse();
    }

    private APIGatewayProxyResponseEvent errorResponse() {
        return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("{\"message\": \"Bad Request\"}");
    }
}