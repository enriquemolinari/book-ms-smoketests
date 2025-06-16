package smoketests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginReservePayAndReceiveEmailSmokeTest {

    public static final String MAILPIT_HOST = "http://localhost:8025";
    public static final String PATH_MAILPIT_MSGS = "/api/v1/messages";
    public static final String PATH_USERS_LOGIN = "/users/login";
    public static final String TOKEN_COOKIE_NAME = "token";
    public static final String PATH_RESERVE_SHOW_ONE = "/shows/private/1/reserve";
    public static final String PATH_PAY_SHOW_ONE = "/shows/private/1/pay";
    public static final String MS_GATEWAY_HOST = "http://localhost:8080";
    public static final String PATH_SHOW_ONE_DETAIL = "/shows/1";
    private int availableSeat;
    private String rawEmail;

    @BeforeEach
    public void before() {
        availableSeat = given()
                .get(PATH_SHOW_ONE_DETAIL)
                .then()
                .statusCode(200)
                .extract()
                .path("currentSeats.find { it.available == true }.seatNumber");

        //delete all messages from mailpit
        given()
                .delete(MAILPIT_HOST + PATH_MAILPIT_MSGS)
                .then()
                .statusCode(200);
    }

    @Test
    @Order(1)
    public void test01() {
        RestAssured.baseURI = MS_GATEWAY_HOST;

        // Login
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body("{ \"username\": \"nico\", \"password\": \"123456789012\" }")
                .post(PATH_USERS_LOGIN);

        loginResponse.then().statusCode(200);
        String token = loginResponse.getCookie(TOKEN_COOKIE_NAME);

        // reserve a seat
        given()
                .contentType(ContentType.JSON)
                .cookie(TOKEN_COOKIE_NAME, token)
                .body(List.of(availableSeat))
                .post(PATH_RESERVE_SHOW_ONE)
                .then()
                .statusCode(200);

        // pay reservation
        given()
                .contentType(ContentType.JSON)
                .cookie(TOKEN_COOKIE_NAME, token)
                .body("""
                        {
                            "selectedSeats": [%d],
                            "creditCardNumber": "1234",
                            "secturityCode": "1234",
                            "expirationYear": 2024,
                            "expirationMonth": 11
                        }
                        """.formatted(availableSeat))
                .post(PATH_PAY_SHOW_ONE)
                .then()
                .statusCode(200);

        // check that the email was sent
        await()
                .atMost(7, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Response emailResponse = given()
                            .contentType(ContentType.JSON)
                            .cookie(TOKEN_COOKIE_NAME, token)
                            .when()
                            .get(MAILPIT_HOST + PATH_MAILPIT_MSGS)
                            .then()
                            .statusCode(200)
                            .body("total", equalTo(1))
                            .extract()
                            .response();
                    String emailId = emailResponse.path("messages[0].ID");

                    rawEmail = given()
                            .contentType(ContentType.JSON)
                            .cookie(TOKEN_COOKIE_NAME, token)
                            .when()
                            .get(MAILPIT_HOST + "/api/v1/message/" + emailId + "/raw")
                            .then()
                            .statusCode(200)
                            .extract()
                            .asString();

                    assertTrue(rawEmail != null);
                });

        assertTrue(rawEmail.contains("Hello nico!"));
        assertTrue(rawEmail.contains("You have new tickets!"));
        assertTrue(rawEmail.contains("Here are the details of your booking:"));
        assertTrue(rawEmail.contains("Movie: Small Fish"));
        assertTrue(rawEmail.contains("Seats: " + availableSeat));
    }
}
