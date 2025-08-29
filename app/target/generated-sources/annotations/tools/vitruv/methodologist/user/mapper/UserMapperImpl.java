package tools.vitruv.methodologist.user.mapper;

import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;
import tools.vitruv.methodologist.apihandler.dto.response.KeycloakWebToken;
import tools.vitruv.methodologist.user.controller.dto.request.UserPostRequest;
import tools.vitruv.methodologist.user.controller.dto.request.UserPutRequest;
import tools.vitruv.methodologist.user.controller.dto.response.UserResponse;
import tools.vitruv.methodologist.user.controller.dto.response.UserWebToken;
import tools.vitruv.methodologist.user.model.User;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2025-08-29T19:19:42+0200",
    comments = "version: 1.6.2, compiler: javac, environment: Java 17.0.14 (JetBrains s.r.o.)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public User toUser(UserPostRequest userDto) {
        if ( userDto == null ) {
            return null;
        }

        User.UserBuilder user = User.builder();

        user.email( userDto.getEmail() );
        user.roleType( userDto.getRoleType() );
        user.username( userDto.getUsername() );
        user.firstName( userDto.getFirstName() );
        user.lastName( userDto.getLastName() );

        return user.build();
    }

    @Override
    public UserResponse toUserResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.id( user.getId() );
        userResponse.email( user.getEmail() );
        userResponse.firstName( user.getFirstName() );
        userResponse.lastName( user.getLastName() );

        return userResponse.build();
    }

    @Override
    public void updateByUserPutRequest(UserPutRequest userPutRequest, User user) {
        if ( userPutRequest == null ) {
            return;
        }

        user.setFirstName( userPutRequest.getFirstName() );
        user.setLastName( userPutRequest.getLastName() );
    }

    @Override
    public UserWebToken toUserWebToken(KeycloakWebToken keycloakWebToken) {
        if ( keycloakWebToken == null ) {
            return null;
        }

        UserWebToken userWebToken = new UserWebToken();

        userWebToken.setAccessToken( keycloakWebToken.getAccessToken() );
        userWebToken.setRefreshToken( keycloakWebToken.getRefreshToken() );
        userWebToken.setExpiresIn( keycloakWebToken.getExpiresIn() );
        userWebToken.setRefreshExpiresIn( keycloakWebToken.getRefreshExpiresIn() );
        userWebToken.setTokenType( keycloakWebToken.getTokenType() );
        userWebToken.setNotBeforePolicy( keycloakWebToken.getNotBeforePolicy() );
        userWebToken.setSessionState( keycloakWebToken.getSessionState() );
        userWebToken.setScope( keycloakWebToken.getScope() );

        return userWebToken;
    }
}
