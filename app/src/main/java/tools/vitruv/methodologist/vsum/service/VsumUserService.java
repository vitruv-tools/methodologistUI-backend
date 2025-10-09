package tools.vitruv.methodologist.vsum.service;

import static tools.vitruv.methodologist.messages.Error.MEMBER_IN_VSUM_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_EMAIL_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.USER_ID_NOT_FOUND_ERROR;
import static tools.vitruv.methodologist.messages.Error.VSUM_ID_NOT_FOUND_ERROR;

import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.vitruv.methodologist.exception.DuplicateVsumMembershipException;
import tools.vitruv.methodologist.exception.NotFoundException;
import tools.vitruv.methodologist.exception.OwnerCannotAddSelfAsMemberException;
import tools.vitruv.methodologist.exception.OwnerRequiredException;
import tools.vitruv.methodologist.exception.OwnerRoleRemovalException;
import tools.vitruv.methodologist.exception.UserAlreadyExistInVsumWithSameRoleException;
import tools.vitruv.methodologist.user.model.User;
import tools.vitruv.methodologist.user.model.repository.UserRepository;
import tools.vitruv.methodologist.vsum.VsumRole;
import tools.vitruv.methodologist.vsum.controller.dto.request.VsumUserPostRequest;
import tools.vitruv.methodologist.vsum.controller.dto.response.VsumUserResponse;
import tools.vitruv.methodologist.vsum.mapper.VsumUserMapper;
import tools.vitruv.methodologist.vsum.model.Vsum;
import tools.vitruv.methodologist.vsum.model.VsumUser;
import tools.vitruv.methodologist.vsum.model.repository.VsumRepository;
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
  VsumRepository vsumRepository;
  VsumUserRepository vsumUserRepository;
  UserRepository userRepository;
  VsumUserMapper vsumUserMapper;

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
  public VsumUser create(Vsum vsum, User user, VsumRole role) {
    if (vsumUserRepository.existsByVsumAndUserAndRole(vsum, user, role)) {
      throw new UserAlreadyExistInVsumWithSameRoleException(
          user.getFirstName().concat(" ").concat(user.getLastName()),
          vsum.getName(),
          role.getName());
    }

    VsumUser vsumUser = VsumUser.builder().role(role).user(user).vsum(vsum).build();
    vsumUserRepository.save(vsumUser);

    return vsumUser;
  }

  /**
   * Deletes all VSUM user relationships associated with the specified VSUM.
   *
   * @param vsum the VSUM whose user relationships should be deleted
   */
  public void delete(Vsum vsum) {
    vsumUserRepository.deleteVsumUserByVsum(vsum);
  }

  /**
   * Retrieves all members associated with the specified VSUM. Throws {@link OwnerRequiredException}
   * if the caller is the owner of the VSUM.
   *
   * @param callerEmail the email of the requesting user
   * @return a list of {@link VsumUserResponse} DTOs representing VSUM members
   * @throws NotFoundException if the caller's email is not found
   * @throws OwnerRequiredException if the caller is the owner of the VSUM
   */
  @Transactional(readOnly = true)
  public List<VsumUserResponse> findAllMemberByVsum(String callerEmail, Long vsumId) {
    var user =
        userRepository
            .findByEmailIgnoreCaseAndRemovedAtIsNull(callerEmail)
            .orElseThrow(() -> new NotFoundException(USER_EMAIL_NOT_FOUND_ERROR));
    var vsumUsers = vsumUserRepository.findAllByVsum_id(vsumId);

    if (!vsumUsers.stream()
        .filter(vsumUser -> vsumUser.getRole().equals(VsumRole.OWNER))
        .map(VsumUser::getUser)
        .toList()
        .contains(user)) {
      throw new OwnerRequiredException();
    }
    return vsumUsers.stream().map(vsumUserMapper::toVsumUserResponse).toList();
  }

  /**
   * Adds a new member to the specified VSUM. Only the owner of the VSUM can perform this operation.
   * Throws an exception if the caller is not the owner, if the owner tries to add themselves, or if
   * the user is already a member.
   *
   * @param callerEmail the email of the user requesting to add a member
   * @param vsumUserPostRequest the request containing VSUM and user details
   * @return the created VSUM user relationship
   * @throws NotFoundException if the VSUM or user is not found
   * @throws OwnerRequiredException if the caller is not the owner of the VSUM
   * @throws IllegalArgumentException if the owner tries to add themselves as a member
   * @throws tools.vitruv.methodologist.exception.DuplicateVsumMembershipException if the user is
   *     already a member of the VSUM
   */
  @Transactional
  public VsumUser addMember(String callerEmail, VsumUserPostRequest vsumUserPostRequest) {
    Vsum vsum =
        vsumRepository
            .findByIdAndRemovedAtIsNull(vsumUserPostRequest.getVsumId())
            .orElseThrow(() -> new NotFoundException(VSUM_ID_NOT_FOUND_ERROR));

    VsumUser callerMembership =
        vsumUserRepository
            .findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsum, callerEmail)
            .orElseThrow(OwnerRequiredException::new);
    if (callerMembership.getRole() != VsumRole.OWNER) {
      throw new OwnerRequiredException();
    }

    User candidate =
        userRepository
            .findByIdAndRemovedAtIsNull(vsumUserPostRequest.getUserId())
            .orElseThrow(() -> new NotFoundException(USER_ID_NOT_FOUND_ERROR));

    if (callerMembership.getUser().getId().equals(candidate.getId())) {
      throw new OwnerCannotAddSelfAsMemberException();
    }

    if (vsumUserRepository.existsByVsumAndVsum_removedAtIsNullAndUserAndUser_RemovedAtIsNull(
        vsum, candidate)) {
      throw new DuplicateVsumMembershipException();
    }

    return create(vsum, candidate, VsumRole.MEMBER);
  }

  /**
   * Removes a member from the specified VSUM. Only the owner of the VSUM can perform this
   * operation. Throws an exception if the caller is not the owner or if attempting to remove the
   * owner.
   *
   * @param callerEmail the email of the user requesting the deletion
   * @param id the ID of the VSUM user relationship to delete
   * @throws NotFoundException if the member to be removed is not found
   * @throws OwnerRequiredException if the caller is not the owner of the VSUM
   * @throws IllegalArgumentException if attempting to remove the owner role
   */
  @Transactional
  public void deleteMember(String callerEmail, Long id) {
    VsumUser vsumUser =
        vsumUserRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException(MEMBER_IN_VSUM_NOT_FOUND_ERROR));

    VsumUser callerMembership =
        vsumUserRepository
            .findByVsumAndUser_EmailAndUser_RemovedAtIsNull(vsumUser.getVsum(), callerEmail)
            .orElseThrow(OwnerRequiredException::new);
    if (callerMembership.getRole() != VsumRole.OWNER) {
      throw new OwnerRequiredException();
    }

    if (vsumUser.getRole().equals(VsumRole.OWNER)) {
      throw new OwnerRoleRemovalException();
    }

    vsumUserRepository.delete(vsumUser);
  }
}
