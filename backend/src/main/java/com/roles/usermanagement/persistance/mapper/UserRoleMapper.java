package com.roles.usermanagement.persistance.mapper;

import com.roles.usermanagement.domain.dto.UserRoleDto;
import com.roles.usermanagement.persistance.entity.UserRoleEntity;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mappings;

@Mapper(componentModel = "spring")
public interface UserRoleMapper {
    @Mappings({

    })
    UserRoleDto toUserRoleDto(UserRoleEntity userRoleEntity);

    List<UserRoleDto> toUserRolesDto(List<UserRoleEntity> userRoleEntities);

}
