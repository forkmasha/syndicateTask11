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
        String eventResource = event.getResource();
        String eventHttpMethod = event.getHttpMethod();
        String eventBody = event.getBody();
        String id = event.getPathParameters() != null ? event.getPathParameters().getOrDefault("id", "") : "";

        System.out.println("INFO: Environment Variables Loaded");
        System.out.println("INFO: REGION = " + System.getenv("REGION"));
        System.out.println("INFO: COGNITO_ID = " + System.getenv("COGNITO_ID"));
        System.out.println("INFO: CLIENT_ID = " + System.getenv("CLIENT_ID"));

        try (CognitoIdentityProviderClient cognitoClient = CognitoIdentityProviderClient.builder()
                .region(Region.of(System.getenv("REGION"))).build()) {

            String userPoolId = System.getenv("COGNITO_ID");
            System.out.println("INFO: Cognito User Pool ID in use: " + userPoolId);

            if (userPoolId == null || userPoolId.isEmpty()) {
                System.err.println("WARNING: Missing or misconfigured Cognito User Pool ID");
            }

            System.out.println("INFO: Processing request");
            System.out.println("INFO: Resource: " + eventResource);
            System.out.println("INFO: HTTP Method: " + eventHttpMethod);
            System.out.println("INFO: Request Body: " + eventBody);

            switch (eventResource) {
                case "/signup":
                    System.out.println("INFO: Handling user signup");
                    return HttpMethod.POST.toString().equals(eventHttpMethod)
                            ? cognitoService.signUp(cognitoClient, userPoolId, eventBody)
                            : badRequestResponse();

                case "/signin":
                    System.out.println("INFO: Handling user signin");
                    return HttpMethod.POST.toString().equals(eventHttpMethod)
                            ? cognitoService.signIn(cognitoClient, userPoolId, eventBody)
                            : badRequestResponse();

                case "/tables":
                    System.out.println("INFO: Handling table creation or retrieval");
                    return HttpMethod.POST.toString().equals(eventHttpMethod)
                            ? databaseService.createTable(eventBody)
                            : databaseService.getTables();

                case "/tables/{id}":
                    System.out.println("INFO: Retrieving table with ID: " + id);
                    return HttpMethod.GET.toString().equals(eventHttpMethod)
                            ? databaseService.getTableById(id)
                            : badRequestResponse();

                case "/reservations":
                    System.out.println("INFO: Handling reservation creation or retrieval");
                    return HttpMethod.POST.toString().equals(eventHttpMethod)
                            ? databaseService.createReservation(eventBody)
                            : databaseService.getReservations();

                default:
                    System.err.println("ERROR: Unknown API endpoint requested - " + eventResource);
                    return badRequestResponse();
            }

        } catch (Exception e) {
            System.err.println("ERROR: An unexpected error occurred during request processing");
            e.printStackTrace();
        }
        return badRequestResponse();
    }

    private APIGatewayProxyResponseEvent badRequestResponse() {
        return new APIGatewayProxyResponseEvent().withStatusCode(400).withBody("{\"error\": \"Invalid request. Please check the API documentation.\"}");
    }
}
