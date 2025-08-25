package tools.vitruv.methodologist.user.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.*;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenByRefreshTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.PostAccessTokenRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.response.UserResponse;
import tools.vitruv.methodologist.user.controller.dto.response.UserWebToken;
import tools.vitruv.methodologist.user.model.*;
import tools.vitruv.methodologist.user.service.UserService;

@WebMvcTest(controllers = UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @MockBean private UserService userService;
  private UserWebToken token;
  private UserResponse userResponse;

  @BeforeEach
  void init() {
    token =
        new UserWebToken(
            "access-abc",
            "refresh-xyz",
            3600,
            86400,
            "Bearer",
            0,
            "session-1",
            "openid profile email");

    userResponse =
        UserResponse.builder()
            .id(1L)
            .email("alice@example.com")
            .firstName("Alice")
            .lastName("Doe")
            .build();
  }

  @Test
  void login_returnsToken_onSuccess() throws Exception {
    when(userService.getAccessToken(any(PostAccessTokenRequest.class))).thenReturn(token);

    mockMvc
        .perform(
            post("/api/v1/users/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(new PostAccessTokenRequest("alice", "p@ss"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token").value("access-abc"))
        .andExpect(jsonPath("$.refresh_token").value("refresh-xyz"))
        .andExpect(jsonPath("$.expires_in").value(3600))
        .andExpect(jsonPath("$.refresh_expires_in").value(86400))
        .andExpect(jsonPath("$.token_type").value("Bearer"))
        .andExpect(jsonPath("$.session_state").value("session-1"))
        .andExpect(jsonPath("$.scope").value("openid profile email"))
        .andExpect(jsonPath("$['not-before-policy']").value(0));

    verify(userService).getAccessToken(ArgumentMatchers.any(PostAccessTokenRequest.class));
  }

  @Test
  void refreshToken_returnsToken_onSuccess() throws Exception {
    when(userService.getAccessTokenByRefreshToken(any(PostAccessTokenByRefreshTokenRequest.class)))
        .thenReturn(token);

    PostAccessTokenByRefreshTokenRequest body =
        new PostAccessTokenByRefreshTokenRequest("refresh-xyz");

    mockMvc
        .perform(
            post("/api/v1/users/access-token/by-refresh-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.access_token", is("access-abc")))
        .andExpect(jsonPath("$.refresh_token", is("refresh-xyz")))
        .andExpect(jsonPath("$.expires_in", is(3600)))
        .andExpect(jsonPath("$.refresh_expires_in", is(86400)))
        .andExpect(jsonPath("$.token_type", is("Bearer")))
        .andExpect(jsonPath("$.session_state", is("session-1")))
        .andExpect(jsonPath("$.scope", is("openid profile email")))
        .andExpect(jsonPath("$['not-before-policy']", is(0)));

    verify(userService)
        .getAccessTokenByRefreshToken(
            ArgumentMatchers.any(PostAccessTokenByRefreshTokenRequest.class));
  }

  @Test
  void signUp_returnsMessage_onSuccess() throws Exception {
    when(userService.create(any(UserPostRequest.class))).thenReturn(new User());

    UserPostRequest body =
        UserPostRequest.builder()
            .firstName("Alice")
            .lastName("Doe")
            .email("alice@example.com")
            .username("alice")
            .password("p@ss")
            .roleType(tools.vitruv.methodologist.user.RoleType.USER)
            .build();

    mockMvc
        .perform(
            post("/api/v1/users/sign-up")
                .with(csrf()) // ← add this
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)))
        .andExpect(status().isOk());

    verify(userService).create(ArgumentMatchers.any(UserPostRequest.class));
  }

  @Test
  @WithMockUser(roles = "user")
  void findById_returnsUser_onSuccess() throws Exception {
    when(userService.findById(1L)).thenReturn(userResponse);

    mockMvc
        .perform(get("/api/v1/users/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id", is(1)))
        .andExpect(jsonPath("$.data.email", is("alice@example.com")));

    verify(userService).findById(1L);
  }

  @Test
  void update_returnsMessage_onSuccess() throws Exception {
    when(userService.update(eq(5L), any(UserPutRequest.class))).thenReturn(new User());

    UserPutRequest body = UserPutRequest.builder().firstName("Alicia").lastName("Doe").build();

    mockMvc
        .perform(put("/api/v1/users/5").contentType(MediaType.APPLICATION_JSON).content(json(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    verify(userService).update(eq(5L), any(UserPutRequest.class));
  }

  @Test
  void delete_returnsMessage_onSuccess() throws Exception {
    when(userService.remove(7L)).thenReturn(new User());

    mockMvc
        .perform(delete("/api/v1/users/7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").exists());

    verify(userService).remove(7L);
  }

  private String json(Object o) throws Exception {
    return objectMapper.writeValueAsString(o);
  }
}
