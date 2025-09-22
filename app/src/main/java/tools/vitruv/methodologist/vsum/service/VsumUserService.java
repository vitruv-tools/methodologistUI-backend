package tools.vitruv.methodologist.vsum.service;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.UserAlreadyExistInVsumWithSameRoleException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumUserRepository;

/**
 * Service class for managing VSUM user relationships. Handles operations related to user
 * associations with Virtual System Under Modification (VSUM).
 */
@Service
@Slf4j
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class VsumUserService {
  VsumUserRepository vsumRepository;

  /**
   * Creates a new VSUM user relationship with the specified parameters. Throws an exception if the
   * user already exists in the VSUM with the same role.
   *
   * @param vsum the VSUM to associate the user with
   * @param user the user to be added
   * @param role the role to assign to the user
   * @return the created VSUM user relationship
   * @throws UserAlreadyExistInVsumWithSameRoleException if the user already exists with the same
   *     role in the VSUM
   */
  @Transactional
  public VsumUser create(Vsum vsum, User user, VsumRole role) {
    if (vsumRepository.existsByVsumAndUserAndRole(vsum, user, role)) {
      throw new UserAlreadyExistInVsumWithSameRoleException(
          user.getFirstName().concat(" ").concat(user.getLastName()),
          vsum.getName(),
          role.getName());
    }

    VsumUser vsumUser = VsumUser.builder().role(role).user(user).vsum(vsum).build();
    vsumRepository.save(vsumUser);

    return vsumUser;
  }
}
