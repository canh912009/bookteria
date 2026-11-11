package com.devteria.profile.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;

import com.devteria.profile.dto.request.ProfileCreationRequest;
import com.devteria.profile.dto.request.UpdateProfileRequest;
import com.devteria.profile.dto.response.UserProfileResponse;
import com.devteria.profile.entity.UserProfile;

@Mapper(componentModel = "spring")
public interface UserProfileMapper {
    UserProfile toUserProfile(ProfileCreationRequest request);

    UserProfileResponse toUserProfileResponse(UserProfile entity);

    void update(@MappingTarget UserProfile entity, UpdateProfileRequest request);
}
