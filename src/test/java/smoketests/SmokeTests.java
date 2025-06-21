package smoketests;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmokeTests {

    public static final String MAILPIT_HOST = "http://localhost:8025";
    public static final String PATH_MAILPIT_MSGS = "/api/v1/messages";
    public static final String PATH_USERS_LOGIN = "/users/login";
    public static final String TOKEN_COOKIE_NAME = "token";
    public static final String PATH_RESERVE_SHOW_ONE = "/shows/private/1/reserve";
    public static final String PATH_SHOWS_BUYER = "/shows/buyer";
    public static final String PATH_PAY_SHOW_ONE = "/shows/private/1/pay";
    public static final String MS_GATEWAY_HOST = "http://localhost:8080";
    public static final String PATH_SHOW_ONE_DETAIL = "/shows/1";
    public static final String PATH_MOVIE_ONE_RATE = "/movies/private/1/rate";
    public static final int AWAIT_TIMEOUT_SECONDS = 7;
    private String rawEmail;

    @BeforeEach
    public void setup() {
        RestAssured.baseURI = MS_GATEWAY_HOST;
    }

    @Test
    public void registerNewUserThenGetICanRateAMovie() {
        // as username cannot be repeated, I will generate a random one
        // a bit fragile test
        var userName = new UserNameRandomGenerator().generate();
        var userId = registerNewUser(userName);
        var token = signInUser("\"" + userName + "\"", "\"123456789012\"");

        var rateRequest = """
                {
                    "rateValue": 5,
                    "comment": "Great Movie"
                }
                """;

        await()
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> given()
                        .contentType(ContentType.JSON)
                        .cookie(TOKEN_COOKIE_NAME, token)
                        .body(rateRequest)
                        .post(PATH_MOVIE_ONE_RATE)
                        .then()
                        .statusCode(200)
                        .body("rateValue", equalTo(5))
                        .body("comment", equalTo("Great Movie"))
                        .body("userId", equalTo(userId.intValue()))
                );
    }


    @Test
    public void registerNewUserThenGetTheNewBuyerFromShowsMs() {
        var userId = registerNewUser(new UserNameRandomGenerator().generate());
        // I can obtain the new buyer from shows ms
        await()
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() -> given()
                        .header("fw-gateway-user-id", userId)
                        .get(PATH_SHOWS_BUYER)
                        .then()
                        .statusCode(200)
                        .body("buyerId", equalTo(userId.intValue()))
                        .body("points", equalTo(0)));
    }

    @Test
    public void createNewMovieInMoviesMsAndGetThatMovieFromShowsMs() {
        var token = signInUser("\"nico\"", "\"123456789012\"");
        // creating new movie
        var nuevaPeliculaRequest = """
                {
                    "name": "New Movie X",
                    "duration": 120,
                    "releaseDate": "2024-06-01",
                    "plot": "a crazy life after...",
                    "genres": ["DRAMA", "ACTION"]
                }
                """;

        Response nuevaPeliculaResponse = given()
                .contentType(ContentType.JSON)
                .cookie(TOKEN_COOKIE_NAME, token)
                .body(nuevaPeliculaRequest)
                .post("/movies/private/new")
                .then()
                .statusCode(200)
                .extract()
                .response();

        int movieId = nuevaPeliculaResponse.path("id");

        await()
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .untilAsserted(() ->
                        given()
                                .get("/shows/movie/" + movieId)
                                .then()
                                .statusCode(200)
                                .body("movieId", equalTo(movieId))
                                .body("shows.size()", equalTo(0)));
    }

    @Test
    public void loginReservePayAndReceiveEmailSmokeTest() {
        //get any first available seat from the show with id 1
        int availableSeat = given()
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

        var token = signInUser("\"nico\"", "\"123456789012\"");

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
                .atMost(AWAIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
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

                    assertNotNull(rawEmail);
                });

        assertTrue(rawEmail.contains("Hello nico!"));
        assertTrue(rawEmail.contains("You have new tickets!"));
        assertTrue(rawEmail.contains("Here are the details of your booking:"));
        assertTrue(rawEmail.contains("Movie: Small Fish"));
        assertTrue(rawEmail.contains("Seats: " + availableSeat));
    }

    private String signInUser(String username, String password) {
        // Login
        Response loginResponse = given()
                .contentType(ContentType.JSON)
                .body("{ \"username\": " + username + ", \"password\": " + password + " }")
                .post(PATH_USERS_LOGIN);

        loginResponse.then().statusCode(200);
        String token = loginResponse.getCookie(TOKEN_COOKIE_NAME);
        return token;
    }

    private Long registerNewUser(String userName) {
        //register a new user
        final String userRegistrationJson = """
                {
                 "name": "ATestUser",
                 "surname": "Surname",
                 "email": "atestuser@email.com",
                 "username": "%s",
                 "password": "123456789012",
                 "repeatPassword": "123456789012"
                }
                """.formatted(userName);

        return given()
                .contentType(ContentType.JSON)
                .body(userRegistrationJson)
                .post("/users/register")
                .then()
                .statusCode(200)
                .extract()
                .as(Long.class);
    }
}
